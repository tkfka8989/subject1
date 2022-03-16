#!/bin/sh

all : server_exe client_exe
CC=gcc

server_exe : servermw.c
  $(CC) -o $@ $<
  
client_exe : clientmw.c
  $(CC) -o $@ $<
  
clean :
  rm server_exe client_exe
