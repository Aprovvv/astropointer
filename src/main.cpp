#include <Arduino.h>
#include <GyverStepper2.h>
//PUPUPU
const size_t MAX_CMD_SIZE = 32;

const int RA_DIR_PIN = 2, RA_STEP_PIN = 3;
const int DEC_DIR_PIN = 2, DEC_STEP_PIN = 3;//FIXME
const int STEPS_PER_REV = 200;

const double RA_GEAR_RATIO = 1, DEC_GEAR_RATIO = 1;


struct command
{
    char cmd_name [MAX_CMD_SIZE];
    int (*cmd_func) ();
};

int Serial_read_word (char* dest, size_t size);

void (*resetFunc) ();

int goto_func ();
int calibrate0_func ();
int calibrate1_func ();
int up_func ();
int down_func ();
int right_func ();
int left_func ();

static bool is_number (const char* str, size_t size);

const command cmd_list[] =
{
    {"goto", goto_func},
    {"calibrate0", calibrate0_func},
    {"calibrate1", calibrate1_func},
    {"up", up_func},
    {"down", down_func},
    {"right", right_func},
    {"left", left_func},
};

void setup()
{
    Serial.begin (9600);
}

void loop()
{
    loopstart:
    char cmd [MAX_CMD_SIZE] = "";
    if (Serial.available())
    {
        Serial_read_word (cmd, MAX_CMD_SIZE);
        for (size_t i = 0; i < sizeof(cmd_list) / sizeof (command); i++)
        {
            if (strcmp (cmd, cmd_list[i].cmd_name) == 0)
            {
                Serial.println ("I know this command!");
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

int calibrate0_func ()
{
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

    Serial_read_word (str, MAX_CMD_SIZE);
    if (strcmp (str, "") != 0)
    {
        Serial.println ("Syntax error: unexpected command after angle value");
        return 1;
    }

    *angle = strtod (str, NULL);
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
    return 0;
}

int right_func ()
{
    return 0;
}

int left_func ()
{
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
