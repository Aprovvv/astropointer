#include <Arduino.h>
#include <SoftwareSerial.h>
#include "GyverStepper2.h"

const size_t MAX_CMD_SIZE = 32;

const int AZ_DIR_PIN = 2, AZ_STEP_PIN = 3;
const int H_DIR_PIN = 4, H_STEP_PIN = 5;
const int STEPS_PER_REV = 200;
const int MICROSTEP = 1;//пишем величину 1/микрошаг, типо 16
const double AZ_GEAR_RATIO = 1, H_GEAR_RATIO = 1;

const int DAYS_IN_MONTH [12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

struct time_date_place
{
    int year;
    int month;
    int date;
    int hour;
    int minute;
    double latitude;
    double longitude;
    double time_zone;
};

struct command
{
    char cmd_name [MAX_CMD_SIZE];
    int (*cmd_func) ();
};

int Serial_read_word (char* dest, size_t size);
int Serial_wait_read_word (char* dest, size_t size);

void (*resetFunc) ();

int goto_func ();
int calibrate1_func ();
int up_func ();
int down_func ();
int right_func ();
int left_func ();
int stop_func ();

static bool is_number (const char* str, size_t size);
static bool is_nat_number (const char* str, size_t size);

double calc_MST (time_date_place input_info);
void AZ_to_EQ (double A, double h, double phi,
               double S, double* alpha, double* delta);
void EQ_to_AZ (double alpha, double delta, double phi,
               double S, double* A, double* h);

const command cmd_list[] =
{
    {"goto", goto_func},
    {"cal", calibrate1_func},
    {"up", up_func},
    {"down", down_func},
    {"right", right_func},
    {"left", left_func},
    {"stop", stop_func},
};

GStepper2<STEPPER2WIRE> AZ_motor(STEPS_PER_REV*MICROSTEP*AZ_GEAR_RATIO,
                                 AZ_STEP_PIN, AZ_DIR_PIN);
GStepper2<STEPPER2WIRE> H_motor(STEPS_PER_REV*MICROSTEP*AZ_GEAR_RATIO,
                                 H_STEP_PIN, H_DIR_PIN);

struct time_date_place input_info = {0};

const int RXPin = 12, TXPin = 13;

SoftwareSerial BTSerial (RXPin, TXPin);

void setup()
{
    Serial.begin (9600);
    BTSerial.begin (9600);

    AZ_motor.setMaxSpeed (100);
    AZ_motor.setAcceleration(50);

    H_motor.setMaxSpeed (100);
    H_motor.setAcceleration(50);

    //тут тестирую, как работают функции для астро расчетов
    input_info = {2025, 8, 14, 5, 29, 48.4827, 135.083, 10};
    Serial.println (calc_MST (input_info), 6);
}

void loop()
{
    loopstart:
    AZ_motor.tick();
    H_motor.tick();
    char cmd [MAX_CMD_SIZE] = "";
    if (BTSerial.available())
    {
        Serial_read_word (cmd, MAX_CMD_SIZE);
        for (size_t i = 0; i < sizeof(cmd_list) / sizeof (command); i++)
        {
            if (strcmp (cmd, cmd_list[i].cmd_name) == 0)
            {
                //Serial.println ("I know this command!");
                cmd_list[i].cmd_func ();
                goto loopstart;
            }
        }
        Serial.print ("Undefined command ");
        Serial.println (cmd);
    }

}

int Serial_read_word(char* dest, size_t size)
{
    size_t i = 0;
    char ch = 48;
    const unsigned long TIMELIMIT = 50;
    unsigned long start_time = 0;

    while (i < size - 1)
    {
        //Ждем, пока чето появится в буфере
        start_time = millis ();
        while (!BTSerial.available ())
        {
            if (millis() - start_time > TIMELIMIT) //если не появилось тикаем
            {
                dest[i] = '\0';
                return i;
            }
        }

        ch = BTSerial.read ();
        if (!isspace (ch) && ch != '\n')
            dest[i++] = ch;
        else
            break;
    }
    dest[i] = '\0';
    return i;
}

int Serial_wait_read_word (char* dest, size_t size)
{
    while (!Serial.available ())
        ;
    return Serial_read_word (dest, size);
}

int goto_func ()
{
    double dest_RA = 0, dest_DEC = 0;
    char str [MAX_CMD_SIZE] = "";
    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected 2 coordinates");
        return 1;
    }

    dest_RA = strtod (str, NULL);

    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected 2 coordinates");
        return 1;
    }

    dest_DEC = strtod (str, NULL);

    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") != 0)
    {
        Serial.println ("Syntax error: unexpected command after coordinates");
        return 1;
    }

    return 0;
}

int calibrate1_func ()
{
    char str [MAX_CMD_SIZE] = "";
    Serial.println ("Enter current date (dd mm yyyy, separated with space)");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_nat_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected number of the day of the month");
        return 1;
    }
    input_info.date = strtol (str, NULL, 0);
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_nat_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected number of the month");
        return 1;
    }
    input_info.month = strtol (str, NULL, 0);
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_nat_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected year");
        return 1;
    }
    input_info.year = strtol (str, NULL, 0);

    Serial.println ("Enter your longitude");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected longitude");
        return 1;
    }
    input_info.longitude = strtod (str, NULL);

    Serial.println ("Enter your latitude");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected latitude");
        return 1;
    }
    input_info.latitude = strtod (str, NULL);

    Serial.println ("Enter your time zone: UTC + ...");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected time zone");
        return 1;
    }
    input_info.time_zone = strtod (str, NULL);

    Serial.println ("Enter time (hh mm, separated with space)");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_nat_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: number of hours");
        return 1;
    }
    input_info.hour = strtol (str, NULL, 0);
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_nat_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: number of minutes");
        return 1;
    }
    input_info.minute = strtol (str, NULL, 0);

    double alpha = 0, delta = 0;

    Serial.println ("Enter current RA (IN HOURS WITH POINT):");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected RA");
        return 1;
    }
    alpha = strtod (str, NULL);

    Serial.println ("Enter current RA (IN DEGREES WITH POINT):");
    Serial_wait_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected DEC");
        return 1;
    }
    delta = strtod (str, NULL);

    double A = 0, h = 0, S = 0;
    S = calc_MST (input_info);
    EQ_to_AZ (alpha, delta, input_info.latitude, S, &A, &h);
    AZ_motor.setCurrent ((int)(A*MICROSTEP*AZ_GEAR_RATIO));
    H_motor.setCurrent ((int)(h*MICROSTEP*H_GEAR_RATIO));

    return 0;
}

int get_move_angle (double* angle)
{
    char str [MAX_CMD_SIZE] = "";
    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") == 0 || !is_number(str, MAX_CMD_SIZE))
    {
        Serial.println ("Syntax error: expected angle value");
        return 1;
    }
    *angle = strtod (str, NULL);

    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") != 0)
    {
        Serial.println ("Syntax error: unexpected command after angle value");
        return 1;
    }
    return 0;
}

int stop_func ()
{
    H_motor.setSpeed (0);
    AZ_motor.setSpeed (0);
    return 0;
}

int up_func ()
{
    /*double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;*/

    //H_motor.setTargetDeg (angle, RELATIVE);
    H_motor.setSpeedDeg (45);
    return 0;
}

int down_func ()
{
    /*double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;*/

    //H_motor.setTargetDeg (-angle, RELATIVE);
    H_motor.setSpeedDeg (-45);
    return 0;
}

int right_func ()
{
    /*double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;

    AZ_motor.setTargetDeg (angle, RELATIVE);*/
    AZ_motor.setSpeedDeg (45);
    return 0;
}

int left_func ()
{
    /*double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;

    AZ_motor.setTargetDeg (-angle, RELATIVE);*/
    AZ_motor.setSpeedDeg (-45);
    return 0;
}

static bool is_number (const char* str, size_t size)
{
    for (size_t i = 0; i < size; i++)
    {
        if (str[i] == '\0')
            return 1;
        if (!isdigit(str[i]) && str[i] != '.')
            return 0;
    }
    return 1;
}

static bool is_nat_number (const char* str, size_t size)
{
    for (size_t i = 0; i < size; i++)
    {
        if (str[i] == '\0')
            return 1;
        if (!isdigit(str[i]))
            return 0;
    }
    return 1;
}

//--------------------------------------------------------------------------------------------------------------

//Тут идут функции для астрономических расчетов
//Я хотел в отдельный файл сначала их выделить, но их мало и чет впадлу

//Все формулы для расчета и обозначения честно скоммунизжены с сайта
//https://web.archive.org/web/20250626023804/http://aa.usno.navy.mil/faq/GAST
//Я не знаю, что меня удивляет больше - что у ВМФ США есть обсерватория,
//или что у них есть методичка по звездному времени

//MST - Mean Siderial Time
//GMST - Greenwich Mean Siderial Time - Гринвичское среднее звездное время
//возвращает в часах
double calc_MST (time_date_place input_info)
{
    int Y = input_info.year;
    int M = input_info.month;
    int D = input_info.date - 1;
    int H = input_info.hour - input_info.time_zone;
    //учет возможного изменения даты, времени и года из-за часового пояса
    if (H < 0)
    {
        H += 24;
        D--;
        if (D < 0)
        {
            M--;
            D += DAYS_IN_MONTH[M];
            if (M < 0)
            {
                Y--;
                M += 12;
            }
            if (Y%4 == 0 && Y%400 != 0 && M == 2)
                D++;
        }
    }
    //DUT - расшифровка хз, смотри сайт. Но это - количество дней, прошедших с 12:00 01.01.2000
    double DUT = D;
    for (int i = 0; i < M - 1; i++)
        DUT += DAYS_IN_MONTH[i];

    /*Вот здесь проблема: Ардуино 32-битный, поэтому double <=> float
    А у float не хватает точности на таких числах (ошибка в 4 знаке)
    Это приводит к ошибке порядка минуты. Можно забить, но я решил учесть
    и сделал не по стандарту - высчитал с большой точностью 01.01.2025 и взял его за точку отсчета*/
    DUT += 365*(Y - 2025);
    DUT += (Y-2025)/4 - (Y-2025)/100 + (Y-2025)/400;//учли високосные
    if (((Y%4 == 0 && Y%100 != 0) || Y%400 == 0) && M < 2)
        DUT--;
    DUT += H/24.0 + input_info.minute/1440.0;
    DUT -= 0.5;//учли, что отсчитывается от полудня 01.01.2000

    double time_since_J2025 = 0.781642 + DUT*1.002737909;
    double GMST = time_since_J2025 - (int)(time_since_J2025);
    double MST = GMST + input_info.longitude/360;

    if (MST > 1) MST--;//учли, что может измениться дата из-за часового пояса
    if (MST < 0) MST++;

    return (MST*24.0);//на вывод удобнее в часах
}

//S и alpha должны быть В ЧАСАХ С ДЕСЯТИЧНОЙ ДРОБЬЮ
void EQ_to_AZ (double alpha, double delta, double phi,
               double S, double* A, double* h)
{
    double rad_alpha = alpha*PI/24.0;
    double rad_delta = delta*PI/180.0;
    double rad_S = S*PI/24.0;
    double rad_phi = phi*PI/180.0;
    double rad_A = 0, rad_h = 0;

    double t_angle = rad_S - rad_alpha;
    rad_h = asin (sin(rad_phi)*sin(rad_delta)
                  + cos(rad_phi)*cos(rad_delta)*cos(t_angle));

    rad_A = asin (cos(rad_delta)*sin(t_angle) / cos(rad_h));//FIXME: сделать азимут от 0 до 360

    *A = rad_A * 180 / PI;
    *h = rad_h * 180 / PI;
}

void AZ_to_EQ (double A, double h, double phi,
               double S, double* alpha, double* delta)
{
    double rad_A = A*PI/180.0;
    double rad_h = h*PI/180.0;
    double rad_S = S*PI/24.0;
    double rad_phi = phi*PI/180.0;
    double rad_alpha = 0, rad_delta = 0;

    double t_angle = acos ( (sin(rad_h) - sin(rad_phi)*sin(rad_delta))
                            / (cos(rad_phi)*cos(rad_delta)));

    rad_alpha = rad_S - t_angle;
    rad_delta = acos (cos(rad_h)*sin(rad_A) / sin(t_angle));

    *alpha = rad_alpha/PI*24.0;
    *delta = rad_delta/PI*180.0;//FIXME: не знаю точно что, но что-то пофиксить точно надо
}
