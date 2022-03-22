#include <mysql/mysql.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#define MAX_SOCK 1024
#define DB_HOST "192.168.10.48"
#define DB_USER "testdb"
#define DB_PASS "testdb123!"
#define DB_NAME "testdb"
#define CHOP(x) x[strlen(x)] = ' '

time_t ct;
struct tm tm;
void writelog(char* log);


int db_input(char id[20], char name[20], char time[40], char ip[30])
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    int       query_stat; 
    char query[255];
    char log_buf[MAX_SOCK];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }

    sql_result = mysql_store_result(connection);
    mysql_free_result(sql_result);

    CHOP(id);
    CHOP(name);
    CHOP(time);
	CHOP(ip);

    sprintf(query, "insert into client values "
                   "('%s', '%s', '%s', '%s', '%s', '%s')",
                   id, name, time, "online", ip, "log_in");

    query_stat = mysql_query(connection, query);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    mysql_close(connection);
}

int db_output(void)
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 
    char log_buf[MAX_SOCK];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }

    query_stat = mysql_query(connection, "select * from client order by login_id");
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    
    sql_result = mysql_store_result(connection);
    
    printf("<***client ID list***>\n");
    sprintf(log_buf, "<***client ID list***>\n");
    writelog(log_buf);
    printf("%-11s %-10s %-10s %-10s %-20s %-10s\n", "login_id", "login_name", "login_time", "chat_state", "login_ip", "login_state");
    sprintf(log_buf, "%-11s %-10s %-10s %-10s %-20s %-10s\n", "login_id", "login_name", "login_time", "chat_state", "login_ip", "login_state");
    writelog(log_buf);
    while ( (sql_row = mysql_fetch_row(sql_result)) != NULL )
    {
        printf("%-11s %-10s %-10s %-10s %-20s %-10s\n", sql_row[0], sql_row[1], sql_row[2], sql_row[3], sql_row[4], sql_row[5]);
        sprintf(log_buf, "%-11s %-10s %-10s %-10s %-20s %-10s\n", sql_row[0], sql_row[1], sql_row[2], sql_row[3], sql_row[4], sql_row[5]);
        writelog(log_buf);
    }
    mysql_free_result(sql_result);
    mysql_close(connection);
}

int db_delete(int del_id)
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 
    char query[255];
    char buf[MAX_SOCK];
    char log_buf[MAX_SOCK];
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    sprintf(buf, "delete from client where login_id = %d",del_id);
    query_stat = mysql_query(connection, buf);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    sql_result = mysql_store_result(connection);
    mysql_free_result(sql_result);
    mysql_close(connection);
}

int db_output_cl(int user)
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 
    char buf[MAX_SOCK];
    char log_buf[MAX_SOCK];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }

    query_stat = mysql_query(connection, "select * from client order by login_id");
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    
    sql_result = mysql_store_result(connection);
    
    sprintf(buf, "<***client ID list***>\n");
    write(user, buf, strlen(buf));
    writelog(buf);
    sprintf(buf, "%-11s %-10s %-10s %-10s %-20s %-10s\n", "login_id", "login_name", "login_time", "chat_state", "login_ip", "login_state");
    write(user, buf, strlen(buf));
    writelog(buf);
    while ( (sql_row = mysql_fetch_row(sql_result)) != NULL )
    {
        sprintf(buf, "%-11s %-10s %-10s %-10s %-20s %-10s\n", sql_row[0], sql_row[1], sql_row[2], sql_row[3], sql_row[4], sql_row[5]);
        write(user, buf, strlen(buf));
        writelog(buf);
        
    }
    
    mysql_free_result(sql_result);
    mysql_close(connection);
}

int db_state(int id, char stat[20])
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 
    char query[255];
    char log_buf[MAX_SOCK];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    sql_result = mysql_store_result(connection);
    mysql_free_result(sql_result);

    CHOP(stat);

    sprintf(query, "update client set chat_state = '%s' where login_id = %d ", stat, id);
    query_stat = mysql_query(connection, query);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    mysql_close(connection);
}

int db_logout(int id)
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 
    char query[255];
    char log_buf[MAX_SOCK];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql connection error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    sql_result = mysql_store_result(connection);
    mysql_free_result(sql_result);

    sprintf(query, "update client set chat_state = '%s' where login_id = %d ", "off_line", id);
    query_stat = mysql_query(connection, query);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }

    sprintf(query, "update client set login_state = '%s' where login_id = %d ", "log_out" , id);
    query_stat = mysql_query(connection, query);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        sprintf(log_buf, "Mysql query error : %s", mysql_error(&conn));
        writelog(log_buf);
        return 1;
    }
    mysql_close(connection);
}


//로그남기기
void writelog(char* log)
{
	char title[100];
	ct = time(NULL);			//현재 시간을 받아옴
	tm = *localtime(&ct);
	sprintf(title, "chat_log_%04d%02d%02d.log",tm.tm_year+1900,tm.tm_mon+1,tm.tm_mday);
	FILE *fp = fopen(title, "a");
	fprintf(fp, "[%02d:%02d:%02d]%s\n", tm.tm_hour, tm.tm_min, tm.tm_sec, log);
	fclose(fp);
}
