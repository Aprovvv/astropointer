#include <Arduino.h>
#include "GyverStepper.h"

const size_t MAX_CMD_SIZE = 32;

const int RA_DIR_PIN = 2, RA_STEP_PIN = 3;
const int DEC_DIR_PIN = 2, DEC_STEP_PIN = 3;//FIXME
const int STEPS_PER_REV = 200;
const int MICROSTEP = 32;//пишем величину 1/микрошаг, типо 16
const double RA_GEAR_RATIO = 1, DEC_GEAR_RATIO = 1;

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

void (*resetFunc) ();

int goto_func ();
int calibrate1_func ();
int up_func ();
int down_func ();
int right_func ();
int left_func ();

static bool is_number (const char* str, size_t size);

double calc_MST (time_date_place input_info);

const command cmd_list[] =
{
    {"goto", goto_func},
    {"calibrate1", calibrate1_func},
    {"up", up_func},
    {"down", down_func},
    {"right", right_func},
    {"left", left_func},
};

GStepper<STEPPER2WIRE> AZ_motor(STEPS_PER_REV*MICROSTEP*RA_GEAR_RATIO,
                                 RA_STEP_PIN, RA_DIR_PIN);

void setup()
{
    Serial.begin (9600);
    AZ_motor.setRunMode (FOLLOW_POS);
    AZ_motor.setMaxSpeed (600);
    AZ_motor.setAcceleration(300);

    //тут тестирую, как работают функции для астро расчетов
    time_date_place input_info = {2025, 8, 14, 5, 29, 48.4827, 135.083, 10};
    //Serial.print ("ST = ");
    //Serial.println (calc_JD (input_info));
    Serial.println (calc_MST (input_info), 6);
}

void loop()
{
    loopstart:
    AZ_motor.tick();
    char cmd [MAX_CMD_SIZE] = "";
    if (Serial.available())
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
        Serial.println ("Undefined command");
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
        while (!Serial.available ())
        {
            if (millis() - start_time > TIMELIMIT) //если не появилось тикаем
            {
                dest[i] = '\0';
                return i;
            }
        }

        ch = Serial.read ();
        if (!isspace (ch) && ch != '\n')
            dest[i++] = ch;
        else
            break;
    }
    dest[i] = '\0';
    return i;
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

int up_func ()
{
    double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;
    return 0;
}

int down_func ()
{
    double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;
    return 0;
}

int right_func ()
{
    double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;

    AZ_motor.setTargetDeg (angle, RELATIVE);
    return 0;
}

int left_func ()
{
    double angle = 0;
    if (get_move_angle (&angle) == 1)
        return 1;

    AZ_motor.setTargetDeg (-angle, RELATIVE);
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

    return (MST);
}
