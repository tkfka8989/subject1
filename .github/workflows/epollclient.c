#include <stdio.h>
#include <stdlib.h>
#include <unistd.h> 
#include <string.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <pthread.h>
#include <time.h>

#define MAXLINE 1000
#define BUF_SIZE 1000
#define NAME_SIZE 2000
	
void * send_msg(void * arg);
void * recv_msg(void * arg);
void error_handling(char * msg);
void write_log(char* log);
time_t ct;
struct tm tm;


char name[NAME_SIZE]="[DEFAULT]";
char msg[BUF_SIZE];
char Num[20];
int main(int argc, char *argv[])
{
	char log_buf[MAXLINE];
	char bufmsg[MAXLINE];
	int sock;
	struct sockaddr_in serv_addr;
	pthread_t snd_thread, rcv_thread;
	void * thread_return;
	if(argc!=4) {
		printf("Usage : %s <IP> <port> <name>\n", argv[0]);
		sprintf(log_buf, "Usage : %s <IP> <port> <name>\n", argv[0]);
		write_log(log_buf);
		exit(1);
	 }
	
	sock=socket(PF_INET, SOCK_STREAM, 0);
	memset(&serv_addr, 0, sizeof(serv_addr));
	serv_addr.sin_family=AF_INET;
	serv_addr.sin_addr.s_addr=inet_addr(argv[1]);
	serv_addr.sin_port=htons(atoi(argv[2]));
	  
	if(connect(sock, (struct sockaddr*)&serv_addr, sizeof(serv_addr))==-1)
		error_handling("connect() error");
	printf("명령어 : user_list(유저목록), exit(종료), on_line(대화가능 상태로 변경), off_line(대화불가능 상태로 변경)\n");
	sprintf(log_buf, "명령어 : user_list(유저목록), exit(종료), on_line(대화가능 상태로 변경), off_line(대화불가능 상태로 변경)\n");
	write_log(log_buf);
	write(sock, argv[3], sizeof(argv[3]));
	sleep(1);
	write(sock, argv[1], strlen(argv[1]));
	memset(bufmsg, 0, sizeof(bufmsg));
	read(sock, bufmsg, MAXLINE);
	
	strncpy(Num, bufmsg, sizeof(bufmsg));
	snprintf(name, 130, "[%s, %s]", Num, argv[3]);

	pthread_create(&snd_thread, NULL, send_msg, (void*)&sock);
	pthread_create(&rcv_thread, NULL, recv_msg, (void*)&sock);
	pthread_join(snd_thread, &thread_return);
	pthread_join(rcv_thread, &thread_return);
	close(sock);  
	return 0;
}
	
void * send_msg(void * arg)   // send thread main
{
	int sock=*((int*)arg);
	char name_msg[NAME_SIZE+BUF_SIZE];
	
	while(1) 
	{
		memset(name_msg, 0 , sizeof(name_msg));
		
		
		if(fgets(msg, BUF_SIZE, stdin)){
			if(!strcmp(msg,"exit\n")||!strcmp(msg,"EXIT\n")) 
			{
				close(sock);
				write_log(msg);
				exit(0);
			}
			
			snprintf(name_msg, 3000, "%s %s", name, msg);
			write(sock, name_msg, strlen(name_msg));
			write_log(name_msg);
		}
	}
	return NULL;
}
	
void * recv_msg(void * arg)   // read thread main
{
	int sock=*((int*)arg);
	char name_msg[NAME_SIZE+BUF_SIZE];
	int str_len;
	while(1)
	{
		str_len=read(sock, name_msg, NAME_SIZE+BUF_SIZE-1);
		if(str_len==-1) 
			return (void*)-1;
		name_msg[str_len]=0;
		write(1, "\033[0G", 4);
		fputs(name_msg, stdout);
		fprintf(stderr, "%s", name);
	}
	return NULL;
}
	
//로그남기기
void write_log(char* log)
{
	char title[100];
	ct = time(NULL);			//현재 시간을 받아옴
	tm = *localtime(&ct);
    //파일 이름 설정
	sprintf(title, "chat_log_%04d%02d%02d.log",tm.tm_year+1900,tm.tm_mon+1,tm.tm_mday);
	FILE *fp = fopen(title, "a");
    //파일에 현재시간을 포함한 콘솔내용 입력
	fprintf(fp, "[%02d:%02d:%02d]%s\n", tm.tm_hour, tm.tm_min, tm.tm_sec, log);
	fclose(fp);
}
	
void error_handling(char *msg)
{
	fputs(msg, stderr);
	fputc('\n', stderr);
	write_log(msg);
	exit(1);
}
