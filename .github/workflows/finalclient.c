#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/time.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <time.h>

#define MAX_SOCK 1024
#define MAXLINE 1000
#define NAME_LEN 20

time_t ct;
struct tm tm;
char *EXIT_STRING = "exit";
// 소켓 생성 및 서버 연결, 생성된 소켓리턴
int tcp_connect(int af, char *servip, unsigned short port);
void errquit(char *mesg) { perror(mesg); exit(1); }
void write_log(char* message);

int main(int argc, char *argv[]) {
	char bufname[NAME_LEN];	// 이름
	char bufmsg[MAXLINE];	// 메시지부분
	char bufall[MAXLINE + NAME_LEN];
	int maxfdp1;	// 최대 소켓 디스크립터
	int s;		// 소켓
	int namelen;	// 이름의 길이
	fd_set read_fds;
	

	if (argc != 4) {
		printf("사용법 : %s sever_ip  port name \n", argv[0]);
		exit(0);
	}

	s = tcp_connect(AF_INET, argv[1], atoi(argv[2]));
	if (s == -1)
		errquit("tcp_connect fail");

	puts("서버에 접속되었습니다.");
	maxfdp1 = s + 1;
	FD_ZERO(&read_fds);
	
	write(s, argv[3], sizeof(argv[3]));
	char Num[20];
	read(s, bufmsg, MAXLINE);
	strncpy(Num, bufmsg, sizeof(bufmsg));
	printf("명령어 : user_list(유저목록), exit(나가기)\n");
	

	while (1) {
		FD_SET(0, &read_fds);
		FD_SET(s, &read_fds);
		if (select(maxfdp1, &read_fds, NULL, NULL, NULL) < 0)
			errquit("select fail");
		if (FD_ISSET(s, &read_fds)) {
			int nbyte;
			if ((nbyte = recv(s, bufmsg, MAXLINE, 0)) > 0) {
				bufmsg[nbyte] = 0;
				write(1, "\033[0G", 4);		//커서의 X좌표를 0으로 이동
				printf("%s", bufmsg);		//메시지 출력
				fprintf(stderr, "[%s, %s]", Num, argv[3]);//내 닉네임 출력

			}
		}
		if (FD_ISSET(0, &read_fds)) {
			if (fgets(bufmsg, MAXLINE, stdin)) {
				fprintf(stderr, "\033[1A"); //Y좌표를 현재 위치로부터 -1만큼 이동
				int ret = snprintf(bufall, 20, "[%s, %s]%s", Num, argv[3], bufmsg);//메시지에 ID 추가
				if (ret < 0) {
         				abort();
    				}
				
				if (send(s, bufall, strlen(bufall), 0) < 0)
					puts("Error : Write error on socket.");
				if (strstr(bufmsg, EXIT_STRING) != NULL) {
					puts("Good bye.");
					close(s);
					exit(0);
				}
			}
		}
	} // end of while
}

int tcp_connect(int af, char *servip, unsigned short port) {
	struct sockaddr_in servaddr;
	int  s;
	// 소켓 생성
	if ((s = socket(af, SOCK_STREAM, 0)) < 0)
		return -1;
	// 채팅 서버의 소켓주소 구조체 servaddr 초기화
	bzero((char *)&servaddr, sizeof(servaddr));
	servaddr.sin_family = af;
	inet_pton(AF_INET, servip, &servaddr.sin_addr);
	servaddr.sin_port = htons(port);

	// 연결요청
	if (connect(s, (struct sockaddr *)&servaddr, sizeof(servaddr))
		< 0)
		return -1;
	return s;
}

