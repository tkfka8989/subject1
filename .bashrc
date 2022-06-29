# .bashrc

# Source global definitions
if [ -f /etc/bashrc ]; then
	. /etc/bashrc
fi

# User specific aliases and functions
force_color_prompts=yes
if [ $TERM = "screen" ]; then
    unset TERMCAP
    export TERM=ansi
fi

export PATH=:$PATH:.:/usr/sbin:/usr/bin
export LD_LIBRARY_PATH=/usr/lib64/:/usr/local/lib:/usr/local/lib64:$LD_LIBRARY_PATH
stty erase ^H

######################################################################
# For Altibase
# ALTIBASE_ENV
#export ALTIBASE_HOME=/home/altibase/altibase_home
export ALTIBASE_HOME=/altibase
export ALTIBASE_PORT_NO=20300
export PATH=${ALTIBASE_HOME}/bin:${PATH}
export LD_LIBRARY_PATH=${ALTIBASE_HOME}/lib:${LD_LIBRARY_PATH}
export CLASSPATH=${ALTIBASE_HOME}/lib/Altibase.jar:${CLASSPATH}
export ALTIBASE_NLS_USE=KO16KSC5601
######################################################################
######################################################################
# For TSUP CMS Package
export TEMPLATE_HOME=/home/mems/MRS-TEMPLETE
export COMCTL_HOME=/home/mems/MRS-TEMPLETE/com-ctl
export DCPACK_HOME=/home/mems/MRS-TEMPLETE/dcpack
export OMCEXT_HOME=/home/mems/MRS-TEMPLETE/omcpack.ext
export EMCPACK_HOME=/home/mems/MRS-TEMPLETE/emspack.1
export PKG_NAME=MPBX_EMS
export PKG_HOME=/home/mems/EMS-project
export CFGHOME=/home/mems/EMS-project/cfg
export PATH=$PATH:/home/mems/EMS-project/bin
export EMSHOME=/home/mems/EMS-project
######################################################################
# For Oracle
export ORACLE_BASE=/oracle
export ORACLE_HOME=$ORACLE_BASE/product/11g
export ORACLE_SID=ORCLEMS
export ORA_NLS33=$ORACLE_HOME/ocommon/nls/admin/data
export TNS_ADMIN=$ORACLE_HOME/network/admin
export LD_LIBRARY_PATH=/lib:/lib64:/usr/lib:/usr/lib64:/usr/local/lib:/usr/local/lib64
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$ORACLE_HOME/lib:$ORACLE_HOME/lib32:
export NLS_LANG=American_America.KO16KSC5601
export CLASSPATH=$ORACLE_HOME/jlib
export PATH=$PATH:/usr/ccs/bin:/usr/sbin:/sbin:/usr/bin:/bin:/usr/X11R6/bin:$ORACLE_HOME/bin

export  EDITOR=/usr/bin/vim

#export  ORACLE_HOME=/home/app/oracle/product/10.2.0/db_1
#export  ORACLE_SID=MRSDB
#export  LD_LIBRARY_PATH=$ORACLE_HOME/lib:$ORACLE_HOME/lib32:/usr/lib:/lib:/usr/local/lib
#export  ORA_NLS11=$ORACLE_HOME/nls/admin/data
#export  PATH=$ORACLE_HOME/bin:$PATH:/usr/local/bin:/usr/ucb:/usr/openwin/bin:/usr/sfw/bin
#export  PATH=$PATH:/bin:/usr/bin:/usr/sbin:/sbin:/usr/ccs/bin:/etc:/usr/lib



export PS1='[\h:\e[44m\u\e[0m:\e[32m${PWD}\e[0m]\$ '
#export term=xterm-color
export PYTHONSTARTUP=$HOME/.pythonrc
#export JAVA_HOME=/usr/local/src/j2sdk1.4.2_19
#
# Add & Change Environment by icbilly, 2013.06.20
#
export JAVA_HOME=/usr/java/jdk1.7.0_25
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=$JAVA_HOME/jre/lib:$JAVA_HOME/lib/tools.jar

export TOMCAT_HOME=/usr/local/tomcat

export LANG=ko_KR.euckr

# ALIASES ##############################################
alias rm='rm -i'
alias so='source ~/.bashrc'
alias ls='ls --color -aF'
alias vi='vim'
alias vir='vim -R'
alias py='python'
alias grep='grep -n'
alias cmake='make clean;make'
alias imake='make clean;make;make install'
alias cms1='isql -s 192.168.10.36 -u CMSDB -p CMSDB'
alias dbs1='isql -s 192.168.10.36 -u TASDB -p TASDB'
alias mdb1='isql -s 121.166.195.239 -u MPXDB -p MPXDB -port 20300 -NLS_USE KO16KSC5601'
alias mdb2='isql -s 121.166.195.240 -u MPXDB -p MPXDB -port 20300 -NLS_USE KO16KSC5601'
alias   cdsrc='cd $HOME/SRC'
alias   cdinc='cd $HOME/SRC/INC'
alias   cddinc='cd $DCPACK_HOME/include'                                                                                      
alias   cddsrc='cd $DCPACK_HOME/src'                                                                                          
alias   cdcinc='cd $COMCTL_HOME/include'                                                                                     
alias   cdcsrc='cd $COMCTL_HOME/src'                                                                                         
alias   cdoinc='cd $OMCEXT_HOME/include'                                                                                 
alias   cdosrc='cd $OMCEXT_HOME/src'                                                                                     
alias   cdlib='cd $HOME/SRC/lib'
alias   cdbin='cd $HOME/bin'
alias   cdlog='cd $HOME/log'
alias   cdcfg='cd $HOME/cfg'
alias   cdsql='cd $HOME/SQL'
alias   cdxuip='cd $HOME/SRC/XUIP'
alias   cdxuim='cd $HOME/SRC/XUIM'
alias   cdstam='cd $HOME/SRC/STAM'
alias   cdstad='cd $HOME/SRC/STAD'
alias   cdck='cd $HOME/SRC/SPIM/client.KYH'
alias   cdspim='cd $HOME/SRC/SPIM'
alias   cdutsim='cd $HOME/SRC/UTSIM'
alias   cdsim='cd $HOME/SRC/SIM'
#alias   db='sqlplus lemsuser/lemsuser'
#alias   mpxdb='sqlplus mpbxdb/mpbxdb@MRSDB'
alias	mpxdb='sqlplus emsuser/orclems5@ORCLEMS'
alias   mktags='cd $HOME/../;ctags -R /usr/include /usr/local/include/ $HOME/../MRS-TEMPLETE/ $HOME/SRC/'

alias   dismc='chkps'
alias   htop='export TERM=xterm-color;htop'
alias   top='export TERM=xterm-color;htop'
alias	cpck="cppcheck -v -D_LINUX_OS -D_REENTRANT --enable=all --language=c "

#
ulimit -c 1000000
#ulimit -q unlimited
set autologout=0

# Make Function
function ccc
{
    gcc -o $1 $1.c;
}

# lsof port check
function lsofport
{
    lsof -w -n -i tcp:$1;
}

function psef
{
    ps -ef | grep $1;
}

function tailf
{
#	task=`echo $1 |cut -c 1-6`
	task=`echo $1`
	tail -f $HOME/log/$task/`date +"%m"`/$1*.`date +"%m%d%H"`;
}

function logs
{
#	task=`echo $1 |cut -c 1-6`
	task=`echo $1`
	vim -R $HOME/log/$task/`date +"%m"`/$1*.`date +"%m%d%H"`;
}

# Added by dduckk
gr() {
	find . -type f -name $2 | xargs grep -n -H $1 2> /dev/null --color=auto
}

gr1() {
	find . -type f -name "$2"
}
