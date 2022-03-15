#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <time.h>
#include <string.h>
#include <stdio.h>
 
#define PORT_NUM 9999
#define EPOLL_SIZE 20
#define MAXLINE 1024
 
char *EXIT_STRING = "exit";
// user data struct
struct udata
{
    int fd;
    char name[80];
};

struct c_list
{
	int ID;
	char username[MAXLINE];
	int in_h;
	int in_m;
	int in_s;

};
struct c_list cli[MAXLINE];
time_t ct;
struct tm tm;
int num_user;

int user_fds[1024];
void send_msg(struct epoll_event ev, char *msg);
 
int main(int argc, char **argv)
{
    struct sockaddr_in addr, clientaddr;
    struct epoll_event ev, *events;            // ev는 listen 소켓의 사건, *event는 
    struct udata *user_data;                // user들의 데이터가 포인터로 처리가 가능하다.
    
    int listenfd;
    int clientfd;
    int i;
    socklen_t addrlen, clilen;
    int readn;
    int eventn;
    int epollfd;
    char buf[MAXLINE];
 
    // events 포인터를 초기화한다. EPOLL_SIZE = 20
    events = (struct epoll_event *)malloc(sizeof(struct epoll_event) * EPOLL_SIZE);
    // epoll 파일 디스크립터를 만든다.
    if((epollfd = epoll_create(100)) == -1)
        return 1;
 
    addrlen = sizeof(addr);
    if((listenfd = socket(AF_INET, SOCK_STREAM, 0)) == -1)
        return 1;
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT_NUM);
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    if(bind (listenfd, (struct sockaddr *)&addr, addrlen) == -1)
        return 1;
 
    listen(listenfd, 5);
 
    // EPOLL_CTL_ADD를 통해 listen 소켓을 이벤트 풀에 추가시켰다.
    ev.events = EPOLLIN;    // 이벤트가 들어오면 알림
    ev.data.fd = listenfd;    // 듣기 소켓을 추가한다.
    epoll_ctl(epollfd, EPOLL_CTL_ADD, listenfd, &ev);    // listenfd의 상태변화를 epollfd를 통해 관찰 
    memset(user_fds, -1, sizeof(int) * 1024);            
 
    while(1)
    {
        // 사건 발생 시까지 무한 대기
        // epollfd의 사건 발생 시 events에 fd를 채운다.
        // eventn은 listen에 성공한 fd의 수
        eventn = epoll_wait(epollfd, events, EPOLL_SIZE, -1); 
        if(eventn == -1)
        {
            return 1;
        }
        for(i = 0; i < eventn ; i++)
        {
            if(events[i].data.fd == listenfd)    // 듣기 소켓에서 이벤트가 발생함
            {
                memset(buf, 0x00, MAXLINE);
                clilen = sizeof(struct sockaddr);
                clientfd = accept(listenfd, (struct sockaddr *)&clientaddr, &clilen);
                user_fds[clientfd] = 1;            // 연결 처리
            
                user_data = malloc(sizeof(user_data));
                user_data->fd = clientfd;
				num_user++;
                
                char *tmp = "First insert your nickname :";
                write(user_data->fd, tmp, 29);
 
                sleep(1);
                read(user_data->fd, user_data->name, sizeof(user_data->name));
                user_data->name[strlen(user_data->name)-1] = 0;
                
                printf("Welcome [%s]  \n", user_data->name);
				
				ct = time(NULL);			//현재 시간을 받아옴
				tm = *localtime(&ct);
				strncpy(cli[clientfd].username, user_data->name, sizeof(user_data->name));
				cli[clientfd].ID = clientfd;
				cli[clientfd].in_h = tm.tm_hour;
				cli[clientfd].in_m = tm.tm_min;
				cli[clientfd].in_s = tm.tm_sec;

				for(i=5 ; i < num_user+5; i++){
					sprintf(buf, "신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d\n", cli[clientfd].ID, cli[clientfd].in_h, cli[clientfd].in_m, cli[clientfd].in_s);
					write(i, buf, strlen(buf));
				
				}

				printf("신규 접속!! id -> %d , 접속시간 -> %02d:%02d:%02d\n", cli[clientfd].ID, cli[clientfd].in_h, cli[clientfd].in_m, cli[clientfd].in_s);
				printf("<***client ID list***>\n");
				printf("    ID    |    USERNAME    |    접속시간    \n");
				for (j = 5;j < num_user+5; j++)
				{
					printf("    %02d    |    %-8s    |    %02d:%02d:%02d \n", cli[j].ID, cli[j].username, cli[j].in_h, cli[j].in_m, cli[j].in_s);
				}


                sleep(1);    
                sprintf(buf, "Okay your nickname : %s\n", user_data->name);
 
                write(user_data->fd, buf, 40);
 
                ev.events = EPOLLIN;
                ev.data.ptr = user_data;
 
                epoll_ctl(epollfd, EPOLL_CTL_ADD, clientfd, &ev);
            }
            else                                // 연결 소켓에서 이벤트가 발생함
            {
                user_data = events[i].data.ptr;
                memset(buf, 0x00, MAXLINE);
                readn = read(user_data->fd, buf, MAXLINE);

                if (readn <= 0) {
					removeClient(i);	// 클라이언트의 종료
					continue;
				}
				buf[nbyte] = 0;
				// 종료문자 처리
				else if (strstr(buf, EXIT_STRING) != NULL) {
					removeClient(i);	// 클라이언트의 종료
					continue;
				}
                else                            // 데이터를 읽는다.
                {
                    send_msg(events[i], buf);
                }
            }
        }
    }
}
 
// client가 보낸 메시지를 다른 client들에게 전송한다.
void send_msg(struct epoll_event ev, char *msg)
{
    int i;
    char buf[MAXLINE+24];
    struct udata *user_data;
    user_data = ev.data.ptr;
    for(i =0; i < 1024; i++)
    {
        memset(buf, 0x00, MAXLINE+24);
        sprintf(buf, "%s : %s", user_data->name, msg);
        if((user_fds[i] == 1))
        {
            write(i, buf, MAXLINE+24);
        }
    }
}
