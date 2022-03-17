#include <mysql/mysql.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#define MAX_SOCK 1024
#define DB_HOST "localhost"
#define DB_USER "testdb"
#define DB_PASS "testdb123!"
#define DB_NAME "testdb"
#define CHOP(x) x[strlen(x)] = ' '

time_t ct;
struct tm tm;
void writelog(char* log);


int db_input(char id[100], char name[100], char time[100])
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    
    int       query_stat; 
    char query[255];
    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        return 1;
    }

    query_stat = mysql_query(connection, "select * from client");
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        return 1;
    }
    
    sql_result = mysql_store_result(connection);
    mysql_free_result(sql_result);

    CHOP(id);
    CHOP(name);
    CHOP(time);

    sprintf(query, "insert into client values "
                   "('%s', '%s', '%s')",
                   id, name, time);

    query_stat = mysql_query(connection, query);
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        return 1;
    }
    
    

    mysql_close(connection);
}

int db_out(void)
{
    MYSQL       *connection=NULL, conn;
    MYSQL_RES   *sql_result;
    MYSQL_ROW   sql_row;
    int       query_stat; 

    
    mysql_init(&conn);

    connection = mysql_real_connect(&conn, DB_HOST,
                                    DB_USER, DB_PASS,
                                    DB_NAME, 3306,
                                    (char *)NULL, 0);

    if (connection == NULL)
    {
        fprintf(stderr, "Mysql connection error : %s", mysql_error(&conn));
        return 1;
    }

    query_stat = mysql_query(connection, "select * from client");
    if (query_stat != 0)
    {
        fprintf(stderr, "Mysql query error : %s", mysql_error(&conn));
        return 1;
    }
    
    sql_result = mysql_store_result(connection);
    
    printf("<***client ID list***>");
    printf("%-11s %-30s %-10s\n", "id", "name", "time");
    while ( (sql_row = mysql_fetch_row(sql_result)) != NULL )
    {
        printf("%-11s %-30s %-10s\n", sql_row[0], sql_row[1], sql_row[2]);
    }
    
    mysql_free_result(sql_result);
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
