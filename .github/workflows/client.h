// usr/include 파일에 위치시킬것

#include <stdio.h>

//client.h
#ifndef CLIENT_H_
#define CLIENT_H_
#define MAX_SOCK 1024

struct c_list
{
	int Num;
	char ID[MAX_SOCK+5];
	int in_h;
	int in_m;
	int in_s;

};


struct c_list cli[MAX_SOCK];
#endif
