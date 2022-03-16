#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <time.h>
#define USER_LIST "user_list"
#define BUF_SIZE 511 
#define EPOLL_SIZE 50
#define MAX_CLNT 100
#define MAX_SOCK 1024
void error_handling(char* buf);
struct c_list
{
	int ID;
	char username[MAX_SOCK+5];
	int in_h;
	int in_m;
	int in_s;

};
struct c_list cli[MAX_SOCK];
time_t ct;
struct tm tm;
int clntCnt=0;	
int maxfdp1 = 0;
void user_list(int user);
int getmax(int sock);
void write_log(char* log);
void clntlist_s(int clnt_sock, int number);

int main(int argc, char* argv[])
{
	int serv_sock, clnt_sock;
	struct sockaddr_in serv_adr, clnt_adr;
	socklen_t adr_sz;
	int str_len, i, j, k;
	char buf[BUF_SIZE];

	int clntNumber[MAX_CLNT];
	int clntCnt=0;	

	struct epoll_event* ep_events;
	struct epoll_event event;
	int epfd, event_cnt;
	
	if(argc!=2)
	{
		printf("Usage : %s <port>\n", argv[0]);
		exit(1);
	}

	serv_sock = socket(PF_INET, SOCK_STREAM, 0);
	memset(&serv_adr, 0, sizeof(serv_adr));
	serv_adr.sin_family = AF_INET;
	serv_adr.sin_addr.s_addr = htonl(INADDR_ANY);
	serv_adr.sin_port = htons(atoi(argv[1]));

	if(bind(serv_sock, (struct sockaddr*) &serv_adr, sizeof(serv_adr))==-1)
		error_handling("bind() error");
	
	if(listen(serv_sock, 5)==-1)
		error_handling("bind() error");

	epfd = epoll_create(EPOLL_SIZE);
	ep_events=malloc(sizeof(struct epoll_event)*EPOLL_SIZE);

	event.events = EPOLLIN;
	event.data.fd = serv_sock;
	epoll_ctl(epfd, EPOLL_CTL_ADD, serv_sock, &event);
	memset(buf, 0 , sizeof(buf));

	while(1)
	{
		event_cnt=epoll_wait(epfd, ep_events, EPOLL_SIZE, -1);
		if(event_cnt==-1)
		{
			puts("epoll_wait() error");
			break;
		}
		
		puts("return epoll_wait (by Level Trigger Method)");
		for(i=0; i<event_cnt; i++)
		{
			if(ep_events[i].data.fd==serv_sock)
			{
				adr_sz=sizeof(clnt_adr);
				clnt_sock = accept(serv_sock, (struct sockaddr*)&clnt_adr, &adr_sz);
				ct = time(NULL);
				tm = *localtime(&ct);
				sprintf(buf, "%d" , clnt_sock);
				send(clnt_sock, buf, strlen(buf), 0);
				read(clnt_sock, buf, MAX_SOCK);
				strncpy(cli[clnt_sock].username, buf, sizeof(buf));
				cli[clnt_sock].ID = clnt_sock;
				cli[clnt_sock].in_h = tm.tm_hour;
				cli[clnt_sock].in_m = tm.tm_min;
				cli[clnt_sock].in_s = tm.tm_sec;
				maxfdp1 = getmax(clnt_sock) + 1;
				event.events = EPOLLIN;
				event.data.fd = clnt_sock;
				epoll_ctl(epfd, EPOLL_CTL_ADD, clnt_sock, &event);
				clntNumber[clntCnt++]=clnt_sock;
				sleep(1);
				for(j=5 ; j < clntCnt+5; j++){
					sprintf(buf, "신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d \n\n", cli[clnt_sock].ID, cli[clnt_sock].in_h, cli[clnt_sock].in_m, cli[clnt_sock].in_s);
					write(j, buf, strlen(buf));
				
				}
				write_log(buf);
				void clntlist_s(clnt_sock, clntCnt);
			}
			else
			{
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
								sprintf(buf, "접속종료!! id -> %d , 종료시간 -> %02d:%02d:%02d\n", cli[ep_events[i].data.fd].ID, tm.tm_hour, tm.tm_min, tm.tm_sec);
								write(j, buf, strlen(buf));
								
							}
							write_log(buf);
							strncpy(cli[ep_events[i].data.fd].username, "NULL", sizeof("NULL"));
							cli[ep_events[i].data.fd].ID = 0;
							cli[ep_events[i].data.fd].in_h = 0;
							cli[ep_events[i].data.fd].in_m = 0;
							cli[ep_events[i].data.fd].in_s = 0;
							clntCnt--;
							break;
						}
					}	
					printf("closed client: %d \n", ep_events[i].data.fd);
					//write_log(buf);
				}
				if (strstr(buf, USER_LIST) != NULL){
					user_list(ep_events[i].data.fd);
					memset(buf, 0 , sizeof(buf));
					
				}
				else
				{
					for(j=0; j<clntCnt; j++)
					{	
						write(clntNumber[j], buf, str_len);
					}
					write_log(buf);
				}
			}
		}
	}
	close(serv_sock);
	close(epfd);
	return 0;
}


//유저리스트
void user_list(int user){
	int i, j;
	char buf[MAX_SOCK];

	sprintf(buf, "<***client ID list***>\n");
	write(user, buf, strlen(buf));
	write_log(buf);
	sprintf(buf, "    ID    |    USERNAME    |    접속시간    \n");
	write(user, buf, strlen(buf));
	write_log(buf);
	for (j = 5;j < maxfdp1; j++){
		int ret = snprintf(buf, 50, "    %02d    |    %-8s    |    %02d:%02d:%02d \n", cli[j].ID, cli[j].username, cli[j].in_h, cli[j].in_m, cli[j].in_s);
		write(user, buf, strlen(buf));
		write_log(buf);
		if (ret < 0) {
         		abort();
    		}
	}
	

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
	sprintf(title, "chat_log_%04d%02d%02d.log",tm.tm_year+1900,tm.tm_mon,tm.tm_mday);
	

	FILE *fp = fopen(title, "a");
	fprintf(fp, "[%02d:%02d:%02d]%s\n", tm.tm_hour, tm.tm_min, tm.tm_sec, log);
	fclose(fp);
}

//서버측 리스트
void clntlist_s(int clnt_sock, int number){
	int k;
	char buf[MACK_SOCK];
	memset(buf, 0, sizeof(buf));
	printf("connected client: %d \n", clnt_sock);
	//write_log(buf);
	printf("<***client ID list***>\n");
	//write_log(buf);
	printf("    ID    |    USERNAME    |    접속시간    \n");
	//write_log(buf);
	for (k = 5;k < number+5; k++)
	{
		printf("    %02d    |    %-8s    |    %02d:%02d:%02d \n", cli[k].ID, cli[k].username, cli[k].in_h, cli[k].in_m, cli[k].in_s);
		//write_log(buf);	
	}

}

void error_handling(char *buf)
{
	fputs(buf, stderr);
	fputc('\n', stderr);
	exit(1);
} 

