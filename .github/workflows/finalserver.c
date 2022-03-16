#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/file.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <time.h>
#include <sys/epoll.h>
#define MAXLINE  511
#define MAX_SOCK 1024 // 솔라리스의 경우 64

char *EXIT_STRING = "exit";	// 클라이언트의 종료요청 문자열
char *START_STRING = "Connected to chat_server \n";	// 클라이언트 환영 메시지
char *USER_LIST = "user_list";	//유저 리스트

int maxfdp1;				// 최대 소켓번호 +1
int num_user = 0;			// 채팅 참가자 수
int num_chat = 0;			// 지금까지 오간 대화의 수
int clisock_list[MAX_SOCK];		// 채팅에 참가자 소켓번호 목록
char ip_list[MAX_SOCK][20];		//접속한 ip목록
int listen_sock;			// 서버의 리슨 소켓

void user_list(int user);							
void addClient(int s, struct sockaddr_in *newcliaddr);	// 새로운 채팅 참가자 처리
int getmax();				// 최대 소켓 번호 찾기
void removeClient(int s);	// 채팅 탈퇴 처리 함수
int tcp_listen(int host, int port, int backlog); // 소켓 생성 및 listen
void errquit(char *mesg) { perror(mesg); exit(1); }
void write_log(char* msg);

time_t ct;
struct tm tm;
struct c_list
{
	int ID;
	char username[MAX_SOCK+5];
	int in_h;
	int in_m;
	int in_s;

};
struct c_list cli[MAX_SOCK];



int main(int argc, char *argv[]) {
	struct sockaddr_in cliaddr;
	char buf[MAXLINE + 1]; //클라이언트에서 받은 메시지
	int i, j, k, nbyte, accp_sock, addrlen = sizeof(struct
		sockaddr_in);
	fd_set read_fds;	//읽기를 감지할 fd_set 구조체


	if (argc != 2) {
		printf("사용법 :%s port\n", argv[0]);
		exit(0);
	}

	// tcp_listen(host, port, backlog) 함수 호출
	listen_sock = tcp_listen(INADDR_ANY, atoi(argv[1]), 5);
	
	while (1) {
		FD_ZERO(&read_fds);
		FD_SET(listen_sock, &read_fds);
		for (i = 0; i < num_user; i++)
			FD_SET(clisock_list[i], &read_fds);

		maxfdp1 = getmax() + 1;	// maxfdp1 재 계산
		if (select(maxfdp1, &read_fds, NULL, NULL, NULL) < 0)
			errquit("select fail");

		if (FD_ISSET(listen_sock, &read_fds)) {
			accp_sock = accept(listen_sock,
				(struct sockaddr*)&cliaddr, &addrlen);
			if (accp_sock == -1) errquit("accept fail");
			addClient(accp_sock, &cliaddr);
			ct = time(NULL);			//현재 시간을 받아옴
			tm = *localtime(&ct);

			sprintf(buf, "%d" , accp_sock);
			send(accp_sock, buf, strlen(buf), 0);
			read(accp_sock, buf, MAX_SOCK);
			strncpy(cli[accp_sock].username, buf, sizeof(buf));

			cli[accp_sock].ID = accp_sock;
			cli[accp_sock].in_h = tm.tm_hour;
			cli[accp_sock].in_m = tm.tm_min;
			cli[accp_sock].in_s = tm.tm_sec;

			for(i=4 ; i < num_user+4; i++){
				sprintf(buf, "신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d\n", 
					cli[accp_sock].ID, cli[accp_sock].in_h, cli[accp_sock].in_m, cli[accp_sock].in_s);
				write(i, buf, strlen(buf));
				write_log(buf);
				
			}

			printf("신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d\n", 
				cli[accp_sock].ID, cli[accp_sock].in_h, cli[accp_sock].in_m, cli[accp_sock].in_s);
			sprintf(buf, "신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d\n", 
				cli[accp_sock].ID, cli[accp_sock].in_h, cli[accp_sock].in_m, cli[accp_sock].in_s);
			write_log(buf);
			printf("<***client ID list***>\n");
			sprintf(buf, "<***client ID list***>\n");
			write_log(buf);
			printf("    ID    |    USERNAME    |    접속시간    \n");
			sprintf(buf, "    ID    |    USERNAME    |    접속시간    \n");
			write_log(buf);
			for (j = 4;j < num_user+4; j++)
			{
				printf("    %02d    |    %-8s    |    %02d:%02d:%02d \n", 
					cli[j].ID, cli[j].username, cli[j].in_h, cli[j].in_m, cli[j].in_s);
				sprintf(buf, "    %02d    |    %-8s    |    %02d:%02d:%02d \n", 
					cli[j].ID, cli[j].username, cli[j].in_h, cli[j].in_m, cli[j].in_s);
				write_log(buf);
			}

		}

		// 클라이언트가 보낸 메시지를 모든 클라이언트에게 방송
		for (i = 0; i < num_user; i++) {
			if (FD_ISSET(clisock_list[i], &read_fds)) {
				num_chat++;				//총 대화 수 증가
				nbyte = recv(clisock_list[i], buf, MAXLINE, 0);
				if (nbyte <= 0) {
					removeClient(i);	// 클라이언트의 종료
					continue;
				}
				// 유저 리스트 처리
				if (strstr(buf, USER_LIST) != NULL){
					user_list(clisock_list[i]);
					continue;
				}
				buf[nbyte] = 0;
				// 종료문자 처리
				if (strstr(buf, EXIT_STRING) != NULL) {
					removeClient(i);	// 클라이언트의 종료
					continue;
				}
				// 모든 채팅 참가자에게 메시지 방송
				for (j = 0; j < num_user; j++){
					send(clisock_list[j], buf, nbyte, 0);
				}
				printf("\033[0G");		//커서의 X좌표를 0으로 이동
				
			}
		}

	}  // end of while

	return 0;
}

//유저리스트
void user_list(int user){
int i, j;
char buf[MAX_SOCK];

sprintf(buf, "<***client ID list***>\n");
write(user, buf, strlen(buf));
sprintf(buf, "    ID    |    USERNAME    |    접속시간    \n");
write(user, buf, strlen(buf));
			
for (j = 4;j < maxfdp1; j++){
		int ret = snprintf(buf, 50, "    %02d    |    %-8s    |    %02d:%02d:%02d \n", cli[j].ID, cli[j].username, cli[j].in_h, cli[j].in_m, cli[j].in_s);
		write(user, buf, strlen(buf));
		if (ret < 0) {
         		abort();
    	}
	}

}


// 새로운 채팅 참가자 처리
void addClient(int s, struct sockaddr_in *newcliaddr) {
	char buf[20];
	inet_ntop(AF_INET, &newcliaddr->sin_addr, buf, sizeof(buf));
	// 채팅 클라이언트 목록에 추가
	clisock_list[num_user] = s;
	strcpy(ip_list[num_user], buf);
	num_user++; //유저 수 증가
}

// 채팅 탈퇴 처리
void removeClient(int s) {
	int k;
	char buf[50];
	close(clisock_list[s]);
	ct = time(NULL);			//현재 시간을 받아옴
	tm = *localtime(&ct);
			
	for(k=4 ; k < maxfdp1; k++){
		sprintf(buf, "유저퇴장!! id -> %d 퇴장시간 -> %02d:%02d:%02d\n", clisock_list[s], tm.tm_hour, tm.tm_min, tm.tm_sec);
		write(k, buf, strlen(buf));
	}
	cli[clisock_list[s]].ID =  0;
	strncpy(cli[clisock_list[s]].username, "NULL", sizeof("NULL"));
	cli[clisock_list[s]].in_h = 0;
	cli[clisock_list[s]].in_m = 0;
	cli[clisock_list[s]].in_s = 0;
					
	if (s != num_user - 1) { //저장된 리스트 재배열
		clisock_list[s] = clisock_list[num_user - 1];
		strcpy(ip_list[s], ip_list[num_user - 1]);
	}
	num_user--; //유저 수 감소
	write(1, "\033[0G", 4);		//커서의 X좌표를 0으로 이동
}

// 최대 소켓번호 찾기
int getmax() {
	// Minimum 소켓번호는 가정 먼저 생성된 listen_sock
	int max = listen_sock;
	int i;
	for (i = 0; i < num_user; i++)
		if (clisock_list[i] > max)
			max = clisock_list[i];
	return max;
}

// listen 소켓 생성 및 listen
int  tcp_listen(int host, int port, int backlog) {
	int sd;
	struct sockaddr_in servaddr;

	sd = socket(AF_INET, SOCK_STREAM, 0);
	if (sd == -1) {
		perror("socket fail");
		exit(1);
	}
	// servaddr 구조체의 내용 세팅
	bzero((char *)&servaddr, sizeof(servaddr));
	servaddr.sin_family = AF_INET;
	servaddr.sin_addr.s_addr = htonl(host);
	servaddr.sin_port = htons(port);
	if (bind(sd, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0) {
		perror("bind fail");  exit(1);
	}
	// 클라이언트로부터 연결요청을 기다림
	listen(sd, backlog);
	return sd;
}

void write_log(char* msg)
{
	char title[100];
	ct = time(NULL);			//현재 시간을 받아옴
	tm = *localtime(&ct);
	sprintf(title, "chat_log_%04d%02d%02d.log",tm.tm_year,tm.tm_mon,tm.tm_mday);
	

	FILE *fp = fopen(title, "a");
	fprintf(fp, "%s\n", msg);
	fclose(fp);
}
