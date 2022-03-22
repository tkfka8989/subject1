#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <time.h>
#include "db_input.h"

#define on_state "on_line"
#define off_state "off_line"
#define USER_LIST "user_list"
#define BUF_SIZE 511 
#define EPOLL_SIZE 50
#define MAX_CLNT 100
#define MAX_SOCK 1024
void error_handling(char* buf);
struct c_list
{
	int ID;
	int in_h;
	int in_m;
	int in_s;

};

char client_id[20];
char client_name[20];
char client_time[20];
char client_ip[30];
char ONLINE[20];
char OFFLINE[20];

struct c_list cli[MAX_SOCK];
time_t ct;
struct tm tm;
int clntCnt=0;	
int maxfdp1 = 0;
int getmax(int sock);
void write_log(char* log);
void client_data(int id, int value, int hour, int min, int sec);

int main(int argc, char* argv[])
{
	strcpy(ONLINE, "online");
	strcpy(OFFLINE, "offline");
	int serv_sock, clnt_sock;
    int str_len, i, j, k;
    int clntNumber[MAX_CLNT];
    int epfd, event_cnt;
	struct sockaddr_in serv_adr, clnt_adr;
	socklen_t adr_sz;
	struct epoll_event* ep_events;
	struct epoll_event event;
	char buf[BUF_SIZE];
	char log_buf[BUF_SIZE];
    char state_buf[BUF_SIZE];
	
	if(argc!=2)
	{
		printf("Usage : %s <port>\n", argv[0]);
		sprintf(log_buf, "Usage : %s <port>\n", argv[0]);
		write_log(log_buf);
		exit(1);
	}

	serv_sock = socket(PF_INET, SOCK_STREAM, 0);
	memset(&serv_adr, 0, sizeof(serv_adr));
	serv_adr.sin_family = AF_INET;
	serv_adr.sin_addr.s_addr = htonl(INADDR_ANY);
	serv_adr.sin_port = htons(atoi(argv[1]));


	if(bind(serv_sock, (struct sockaddr*) &serv_adr, sizeof(serv_adr))==-1)
	{
		error_handling("bind() error");
	}
	if(listen(serv_sock, 5)==-1)
	{
		error_handling("bind() error");
	}
	epfd = epoll_create(EPOLL_SIZE);
	ep_events=malloc(sizeof(struct epoll_event)*EPOLL_SIZE);

	event.events = EPOLLIN;
	event.data.fd = serv_sock;
	epoll_ctl(epfd, EPOLL_CTL_ADD, serv_sock, &event);
    printf("서버에 연결되었습니다.\n");
    sprintf(log_buf, "서버에 연결되었습니다.\n");
    write_log(log_buf);

	while(1)
	{
		event_cnt=epoll_wait(epfd, ep_events, EPOLL_SIZE, -1);
		if(event_cnt==-1)
		{
			error_handling("epoll_wait() error");
			break;
		}
		
		
		for(i=0; i<event_cnt; i++)
		{
            memset(buf, 0 , sizeof(buf));
			if(ep_events[i].data.fd==serv_sock)
			{
                
				adr_sz=sizeof(clnt_adr);
				clnt_sock = accept(serv_sock, (struct sockaddr*)&clnt_adr, &adr_sz);
				ct = time(NULL);
				tm = *localtime(&ct);
				sprintf(buf, "%d" , clnt_sock);
				send(clnt_sock, buf, strlen(buf), 0);
				read(clnt_sock, buf, MAX_SOCK);
				snprintf(client_name,550,"%s", buf);
                db_delete(clnt_sock);
				client_data(clnt_sock, clnt_sock, tm.tm_hour, tm.tm_min, tm.tm_sec);
				sleep(1);
				read(clnt_sock, buf, MAX_SOCK);
				sprintf(client_ip,"%s",buf);
				sprintf(client_id,"%d",clnt_sock);
				sprintf(client_time,"%02d:%02d:%02d", tm.tm_hour, tm.tm_min, tm.tm_sec);
				db_input(client_id, client_name, client_time, client_ip);
				
				maxfdp1 = getmax(clnt_sock) + 1;
				event.events = EPOLLIN;
				event.data.fd = clnt_sock;
				epoll_ctl(epfd, EPOLL_CTL_ADD, clnt_sock, &event);
				clntNumber[clntCnt++]=clnt_sock;
				printf("LOGIN! id -> %d , 접속시간 -> %02d:%02d:%02d \n", cli[clnt_sock].ID, cli[clnt_sock].in_h, cli[clnt_sock].in_m, cli[clnt_sock].in_s);
				for(j=5 ; j < clntCnt+5; j++){
					sprintf(buf, "LOGIN! id -> %d , 접속시간 -> %02d:%02d:%02d \n", cli[clnt_sock].ID, cli[clnt_sock].in_h, cli[clnt_sock].in_m, cli[clnt_sock].in_s);
					write(j, buf, strlen(buf));
				}
				write_log(buf);
				db_output();
				
			}
            
			else
			{
                memset(buf, 0 , sizeof(buf));
				str_len=read(ep_events[i].data.fd, buf, BUF_SIZE);
				if(str_len==0)
				{
					epoll_ctl(epfd, EPOLL_CTL_DEL, ep_events[i].data.fd, NULL);
					close(ep_events[i].data.fd);
					for(j=0; j<clntCnt; j++)
					{
						if(clntNumber[j]==ep_events[i].data.fd)
						{
							memcpy(&clntNumber[j], &clntNumber[j+1], clntCnt-(j+1));
							ct = time(NULL);
							tm = *localtime(&ct);
							for(k=5 ; k < maxfdp1; k++){
								sprintf(state_buf, "접속종료!! id -> %d , 종료시간 -> %02d:%02d:%02d\n", cli[ep_events[i].data.fd].ID, tm.tm_hour, tm.tm_min, tm.tm_sec);
								write(k, state_buf, strlen(state_buf));
								
							}
							db_logout(ep_events[i].data.fd);
							client_data(ep_events[i].data.fd, 0, 0, 0, 0);
							clntCnt--;
							break;
						}
					}
					
					printf("closed client ID: %d \n", ep_events[i].data.fd);
					sprintf(log_buf, "closed client ID: %d \n", ep_events[i].data.fd);
					write_log(log_buf);
					write_log(buf);
				}
				
				if (strstr(buf, on_state) != NULL){
                    write_log(buf);
					db_state(ep_events[i].data.fd, ONLINE);
                    sprintf(state_buf, "대화 상태가 %s로 변경되었습니다.\n", ONLINE);
                    write(ep_events[i].data.fd, state_buf, strlen(state_buf));
                    write_log(state_buf);
					break;
				}
				if (strstr(buf, off_state) != NULL){
                    write_log(buf);
					db_state(ep_events[i].data.fd, OFFLINE);
                    sprintf(state_buf, "대화 상태가 %s로 변경되었습니다.\n", OFFLINE);
                    write(ep_events[i].data.fd, state_buf, strlen(state_buf));
                    write_log(state_buf);
					break;
				}
				if (strstr(buf, USER_LIST) != NULL){
					write_log(buf);
					db_output_cl(ep_events[i].data.fd);
					
				}
				else
				{
					for(j=0; j<clntCnt; j++)
					{	
						write(clntNumber[j], buf, str_len);
					}
					write_log(buf);
				}
			memset(buf, 0 , sizeof(buf));
			}
		}
	}
	close(serv_sock);
	close(epfd);
	return 0;
}



// 최대 소켓번호 찾기
int getmax(int sock) {
	// Minimum 소켓번호는 가정 먼저 생성된 listen_sock
	int max = 0;
	int i;
	if (sock > max)
		max = sock;
	return max;
}

//로그남기기
void write_log(char* log)
{
	char title[100];
	ct = time(NULL);			//현재 시간을 받아옴
	tm = *localtime(&ct);
	sprintf(title, "chat_log_%04d%02d%02d.log",tm.tm_year+1900,tm.tm_mon+1,tm.tm_mday);
	FILE *fp = fopen(title, "a");
	fprintf(fp, "[%02d:%02d:%02d]%s\n", tm.tm_hour, tm.tm_min, tm.tm_sec, log);
	fclose(fp);
}

void client_data(int id, int value, int hour, int min, int sec){

    cli[id].ID = value;
	cli[id].in_h = hour;
	cli[id].in_m = min;
	cli[id].in_s = sec;

}

void error_handling(char *buf)
{
	fputs(buf, stderr);
	fputc('\n', stderr);
	write_log(buf);
	exit(1);
} 

