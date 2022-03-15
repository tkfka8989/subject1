#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <netinet/in.h>
#include <arpa/inet.h>
 
#define MAXLINE 1024
char *EXIT_STRING = "exit";
 
int main(int argc, char **argv)
{
  
  int socketfd;
  struct sockaddr_in sockaddr;
  int socklen;
  fd_set readfds, oldfds;
  int maxfd;
  int fd_num;
  char buf[MAXLINE];
  int readn, writen;
 
  if(argc != 3)
  {
      printf("Usage : %s [ip address] [port]\n", argv[0]);
      return 1;
  }
  if( (socketfd = socket(AF_INET, SOCK_STREAM, 0)) < 0 )
  {
      return 1;
  }
 
  sockaddr.sin_family = AF_INET;
  sockaddr.sin_addr.s_addr = inet_addr(argv[1]);
  sockaddr.sin_port = htons(atoi(argv[2]));
 
  socklen = sizeof(sockaddr);
  
  if(connect(socketfd, (struct sockaddr *)&sockaddr, socklen) == -1)
  {        perror("connect error");
         return 1;
  }
  
  // readfd set 0 
  FD_ZERO(&readfds);
  // socketfd에 해당하는 readfds의 비트를 1로 한다.
  FD_SET(socketfd, &readfds);
  // 0번째 인자를 1로 set한다
  FD_SET(0, &readfds);
 
  maxfd = socketfd;
  oldfds = readfds;
 
  while(1)
  {
      readfds = oldfds;
      fd_num = select(maxfd + 1, &readfds, NULL, NULL, NULL);
      if(FD_ISSET(socketfd, &readfds))        // 초기 연결인지 아닌지 검사를 한다.
      {
          memset(buf, 0x00, MAXLINE);
          readn = read(socketfd, buf, MAXLINE);
          if(readn <= 0)
          {
              return 1;
          }
          writen = write(1, buf, readn);
          if(writen != readn)
          {
              return 1;
          }
      }
      if(FD_ISSET(0, &readfds))                // raedfds의 0번째 인자가 set?
      {
          memset(buf, 0x00, MAXLINE);
          readn = read(0, buf, MAXLINE);
          printf("> %s", buf);
          if(readn <= 0)
          {
              return 1;
          }
          writen = write(socketfd, buf, readn);
		  if (strstr(buf, EXIT_STRING) != NULL) {
				puts("Good bye.");
				close(socketfd);
				exit(0);
		  }
          if(readn != writen)
          {
              return 1;
          }
      }
  }
}
