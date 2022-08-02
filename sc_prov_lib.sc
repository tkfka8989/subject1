#include <sqlcli.h>
#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <sys/time.h>
#include <errno.h>
#include <signal.h>
#include "common_lendef.h"
#include "debug.h"
#include "gendef.h"
#include "altibase_config.h"
#include "threadinfo.h"
#include "sub_cmd_rep.h"
#include "provision_etc.h"
#include "mpx_provision.h"	// MPBX 청약 구조체 정의
#include "color.h"
#include "cJSON.h"

int _conv_from_UTF8_to_eucKR(char *nick, int nick_len, char *conv_nick, int *conv_nick_len);
int _conv_from_eucKR_to_eucKR(char *nick, int nick_len, char *conv_nick, int *conv_nick_len);

typedef struct _subSvcTbl{
	int		key;
	char	svc_name[20];
	int		svc_idx_num;
	int		svc_status;
}subSvcTbl;

///////////////////////////////////////////////////////////////////////////////
subSvcTbl subSvcList[]={
	//2016.07.19 v0.99 착신전환 청약 제외.
	//{1, "MPBX2010", 1, 0},		// 착신전환				, 두번째
	{2, "MPBX2020", 2, 1},		// 유무선동시착신허용여부	, 세번째
	{3, "MPBX2030", 3, 1},		// 돌려주기 허용여부		, 네번째
	{4, "MPBX2040", 4, 0},		// 3자통화 허용여부			, 다섯번째
	{5, "MPBX2050", 5, 0},		// 그룹통화 허용여부		, 여섯번째
	/* START, Ver 1.0.8, 2018.02.01 */
	//{6, "MPBX2060", 9, 1},		// 무선단말 착신        , 열번째, 부가서비스 코드 없음 	//reserved
	//{7, "MPBX2070", 10, 0},		// 미할당													//reserved
	{6, "MPBX2060", 6, 0},		// PC 동시착신				, 일곱번째
	{7, "MPBX2070", 7, 0},		// 패드 동시착신			, 여덟번째
	/* START, Ver 1.1.2, 2018.09.10 */
	{8, "MPBX2080", 8, 0},		// 유선전화연결설정			, 아홉번째
	/* END, Ver 1.1.2, 2018.09.10 */
	/* END, Ver 1.0.8, 2018.02.01 */
	{0, "NULL", 0, 0}
};
///////////////////////////////////////////////////////////////////////////////

int _get_subsvc_index(char *svc_name)
{
	int i=0;
	while(1){
		if(subSvcList[i].key==0)
			break;
		if(!strcasecmp(subSvcList[i].svc_name, svc_name))
			return subSvcList[i].svc_idx_num;
		i++;
	}
	return -1;
}

int _get_svc_status_by_index(int svc_index)
{
	int i=0;
	while(1){
		if(subSvcList[i].key==0)
			break;
		if(subSvcList[i].svc_idx_num == svc_index)
			return subSvcList[i].svc_status;
		i++;
	}
	return -1;
}

int _get_svc_name_by_index(int svc_index, char *svc_name)
{
	int i=0;
	while(1){
		if(subSvcList[i].key==0)
			break;
		if(subSvcList[i].svc_idx_num == svc_index)
		{
			strcpy(svc_name, subSvcList[i].svc_name);
			return subSvcList[i].svc_status;
		}
		i++;
	}
	return -1;
}

int _make_subsvc_str(char *sub_svc)
{
	int i=0;
	sprintf(sub_svc, "%020d", 0);

	while(1){
		if(subSvcList[i].key==0)
			break;

		//ASCII code로 연산해서 정수형을 char로 변형.
		sub_svc[subSvcList[i].svc_idx_num] = subSvcList[i].svc_status+'0';
		i++;
	}
	return 1;
}

int _set_subsvc_str(char *sub_svc, int idx, int state)
{
	sub_svc[idx-1] = state+'0';
	return 1;
}

/* START, Ver 1.1.2, 2018.09.10 */
int _get_cfg_subsvc_status(char *cfg_status){
	int i = 0;
	int ret = 0;
	char    file_name[FILENAME_LEN+1]="";
	char    tmp[64+1]="";
	char    str[64+1]="";

	if(getenv("PKG_HOME")==NULL)
	{
		fprintf(stderr, "PKG_HOME Enviroment varialble not declare, setting .bashrc or .cshrc\n");
		return -1;
	}

	sprintf(file_name, PROV_INFO_CFG, getenv("PKG_HOME"));

	sprintf(str, "MPBX2080_DEFAULT:");
	ret = _mrs_get_svcconf_item(file_name, str, tmp);
	if(ret < 0)
	{
		_mrs_logprint(DEBUG_1, "ERROR> Para Error : %s So exit ....\n", str);
		sprintf(cfg_status, "%s", "0");
		_mrs_logprint(DEBUG_6, "FAIL> cfg get subsvc Status : default [%s]\n",cfg_status);
		return -1;
	}
	_mrs_logprint(DEBUG_6, "SUCC> cfg get subsvc Status : [%s]\n",tmp);
	sprintf(cfg_status, "%s",tmp);

	return 1;

}
/* END, Ver 1.1.2, 2018.09.10 */

/* START, Ver 1.1.4, 2022.06.15 */
int compare_chr_len(int json_len, int column_len, char *iden_name, char *iden_value){
	/*  길이 초과 에러 보류, 20220629
	if (json_len > (column_len -1)){
		_mrs_logprint(DEBUG_1, "%s(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", iden_name, iden_value, column_len, iden_value);
		return -1;
	}
	*/
	return 1;

}
/* END, Ver 1.1.4, 2022.06.15 */

void print_mis_para(const char *func_str, char *str)
{
	_mrs_logprint(DEBUG_2, "%s%s()%s PROV ERROR - MISSING PARAM: %s[%s]%s\n",
		SKY_COLOR, func_str, NORMAL_COLOR, RED_COLOR, str, NORMAL_COLOR);
}

void print_null_value(const char *func_str, char *parm_name, char *val)
{
	/*
	_mrs_logprint(DEBUG_2, "%s%s()%s PROV ERROR - PARAMETER VALUE IS NULL: %s[%s]%s ===>[%s]\n",
		SKY_COLOR, func_str, NORMAL_COLOR, RED_COLOR, parm_name, NORMAL_COLOR,val);
	*/
	_mrs_logprint(DEBUG_2, "(%s) PROV ERROR - PARAMETER VALUE IS NULL: PARM Name[%s] \n", func_str, parm_name);
}

/* 신규 mPBX 사업자 추가 */
/* 필수항목 정의 ---------------------------------------------
1. 모상품 계약 아이디(TOP_SA_ID)               - top_said,
2. mPBX 계약 고객사 고유번호(mPBX_CUST_ID)     - cust_id,
3. 고객사 유선전화 이용 유형(NUM_TYPE)         - num_type,
4. 고객사 유선대표번호(REP_NUM)                - rep_num,
5. 계약 mPBX 이용자수 (MPBX_USR_NUM)           - max_user_num,
6. 기업대표번호(mPBX_RN                        - mpbx_rn,
7. 모상품 상태(TOP_SAID_STATUS)                - cust_status
------------------------------------------------------------*/
int prov_proc_add_cust(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		cust_status;
	int		max_user_num;
	int     nCount;

	char	said[24+1];
	char	cust_id[20+1];
	char	cust_name[100*2+1];
	char	num_type[30+1];
	char	rep_num[30+1];
	char	rep_lnp_num[24+1];
    //char    ins_date[8+1];
    char    rdate[14+1];
	char	ext_num[24+1];
	char	ext_lnp_num[24+1];
	char	cid_text[30+1];
	char	tmp_cid_text[30+1];
	int		rep_flag;
	/* START, Ver 1.1.4, 2022.06.15 */
	int		svc_type;
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	int		i;
	int		ext_num_cnt=0;
	int		ext_lnp_num_cnt=0;
	int		len_name=0;
	int		len_cid_text = 0;
	char	*pchar;
    char    temp[256+1];
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;


	if(!root)
		return ERR_UNEXPECTED;

	memset(said, 	0x00, sizeof(said));
	memset(cust_id, 	0x00, sizeof(cust_id));
	memset(cust_name, 	0x00, sizeof(cust_name));
	memset(num_type, 	0x00, sizeof(num_type));
	memset(rep_num, 	0x00, sizeof(rep_num));
	memset(rep_lnp_num, 0x00, sizeof(rep_lnp_num));
	//memset(ins_date, 	0x00, sizeof(ins_date));
	memset(rdate, 		0x00, sizeof(rdate));
	memset(ext_num, 	0x00, sizeof(ext_num));
	memset(ext_lnp_num, 0x00, sizeof(ext_lnp_num));
	memset(temp, 		0x00, sizeof(temp));
	memset(cid_text, 	0x00, sizeof(cid_text));
	memset(tmp_cid_text, 	0x00, sizeof(tmp_cid_text));

	/* mPBX_CUST_ID */
	item = cJSON_GetObjectItem(root, "mPBX_CUST_ID");
	if(!item){ print_mis_para(__func__, "mPBX_CUST_ID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_id), "MPBX_CUST_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(cust_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_CUST_ID ---------------->[%s]\n", cust_id);
	item=NULL;


	/* SA_ID */
	item = cJSON_GetObjectItem(root, "SA_ID");
	if(!item){ print_mis_para(__func__, "SA_ID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "SA_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(said), "SA_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(said, item->valuestring);
	_mrs_logprint(DEBUG_5, "SA_ID ----------------------->[%s]\n", said);
	item=NULL;

	/* CUST_NAME */
	item = cJSON_GetObjectItem(root, "CUST_NAME");
	if(!item){ print_mis_para(__func__, "CUST_NAME"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "CUST_NAME", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_name), "CUST_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
	_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), cust_name, &len_name);
	//strcpy(cust_name, item->valuestring);
	_mrs_logprint(DEBUG_5, "CUST_NAME ------------------->[%s]\n", cust_name);
	item=NULL;

	/* NUM_TYPE */
	item = cJSON_GetObjectItem(root, "NUM_TYPE");
	if(!item){ print_mis_para(__func__, "NUM_TYPE"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "NUM_TYPE", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(num_type), "NUM_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(num_type, item->valuestring);
	_mrs_logprint(DEBUG_5, "NUM_TYPE -------------------->[%s]\n", num_type);
	item=NULL;

	/* REP_NUM */
	item = cJSON_GetObjectItem(root, "REP_NUM");
	if(!item){ print_mis_para(__func__, "REP_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "REP_NUM", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(rep_num), "REP_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(rep_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "REP_NUM --------------------->[%s]\n", rep_num);
	item=NULL;

	/* REP_LNP_NUM */
	item = cJSON_GetObjectItem(root, "REP_LNP_NUM");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(rep_lnp_num), "REP_LNP_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(rep_lnp_num, item->valuestring);
		else rep_lnp_num[0] = 0x00;
		_mrs_logprint(DEBUG_5, "REP_LNP_NUM ----------------->[%s]\n", rep_lnp_num);
		item=NULL;
	}

	/* mPBX_STATE */
	item = cJSON_GetObjectItem(root, "mPBX_STATE");
	if(!item){ print_mis_para(__func__, "mPBX_STATE"); goto no_required;}
	if(item->type == cJSON_Number) cust_status = item->valueint; else cust_status = atoi(item->valuestring);
	_mrs_logprint(DEBUG_5, "CUST_STATUS ----------------->[%d]\n", cust_status);
	item=NULL;

	/* START, Ver 1.1.4, add svc_type, max_user_num json */
	/* SVC_TYPE */
	item = cJSON_GetObjectItem(root, "SVC_TYPE");
	//if(!item){ print_mis_para(__func__, "SVC_TYPE"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) svc_type = item->valueint; else svc_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "SVC_TYPE ----------------->[%d]\n", svc_type);
		item=NULL;
	}else{
		svc_type = 0;
	}


	/* MPBX_USR_NUM */
	item = cJSON_GetObjectItem(root, "MPBX_USR_NUM");
	//if(!item){ print_mis_para(__func__, "MPBX_USR_NUM"); goto no_required;}
	if(item){
        if(item->type == cJSON_Number) max_user_num = item->valueint; else max_user_num = atoi(item->valuestring);
        _mrs_logprint(DEBUG_5, "MPBX_USR_NUM ----------------->[%d]\n", max_user_num);
        item=NULL;
    }else{
        max_user_num = 0;
    }
	/* END, Ver 1.1.4, add svc_type, max_user_num json */

	//_mrs_sys_datestring_day(ins_date);

	sprintf(tmp_cid_text, "%s", "업무통화");
	_conv_from_eucKR_to_eucKR(tmp_cid_text, strlen(tmp_cid_text), cid_text, &len_cid_text);
	_mrs_logprint(DEBUG_5, "CID_TEXT -------------------->[%s]\n", cid_text);

	/* mPBX_CUST_ID가 존재하는지 확인. */
	EXEC SQL AT :sessionid
			SELECT 	COUNT(*)
			INTO 	:nCount
			FROM 	MPX_CUSTOMINFO
			WHERE 	CUST_ID=:cust_id;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_cust] Select db(MPX_CustomInfo) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	/* START, Ver 1.1.4, add svc_type, max_user_num */
	if(nCount > 0){
		/* 이미 고객사가 있을 경우 고객사 테이블은 업데이트 처리한다. (SA_ID 추가발생할 경우 고객사정보는 중복해서 등록요청됨) */
		/*******************************************************************************************************************/
		/* MPX_CustomInfo Table에 Update */
		/*******************************************************************************************************************/
		EXEC SQL AT :sessionid
				UPDATE 	MPX_CUSTOMINFO
				SET 	CUST_STATUS=:cust_status, CUST_NAME=:cust_name, UPDATE_DATE = TO_CHAR (SYSDATE, 'YYYYMMDDHHMISS'), SVC_TYPE = :svc_type, MPBX_USR_NUM = :max_user_num
				WHERE 	CUST_ID=:cust_id;
		/*******************************************************************************************************************/
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo UPDATE FAIL. CUST_ID[%s], SA_ID[%s] - [%s][%d] %s\n",
														cust_id, said, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo UPDATE SUCCESS. CUST_ID[%s], SA_ID[%s] - [%s]\n", cust_id, said, sessionid);
	}
	else
	{
		/*******************************************************************************************************************/
		/* MPX_CustomInfo Table에 INSERT */
		/*******************************************************************************************************************/
		/* 2016.09.23 modified by shyoun. set GCALL_FLAG default 0 */
		EXEC SQL AT :sessionid
				INSERT INTO MPX_CUSTOMINFO
						(CUST_ID, CUST_STATUS, CUST_NAME, CID_TEXT, INS_DATE, UPDATE_DATE, GCALL_FLAG, SVC_TYPE, MPBX_USR_NUM)
				VALUES	(:cust_id, :cust_status, :cust_name, :cid_text, TO_CHAR (sysdate, 'YYYYMMDD'), TO_CHAR (sysdate, 'YYYYMMDDHHMI'), 2, :svc_type, :max_user_num);
		/*******************************************************************************************************************/
		/* END, Ver 1.1.4, add svc_type, max_user_num */
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo INSERT FAIL - [%s][%d] %s\n", sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo INSERT SUCCESS. CUST_ID[%s], SA_ID[%s] - [%s]\n", cust_id, said, sessionid);
	}

	/* EXT_NUM */
	cJSON *ext = cJSON_GetObjectItem(root, "EXT_NUM");
	if(!ext){ print_mis_para(__func__, "EXT_NUM"); goto no_required;}

	ext_num_cnt = cJSON_GetArraySize(ext);

	/* EXT_LNP_NUM */
	cJSON *ext_lnp = cJSON_GetObjectItem(root, "EXT_LNP_NUM");
	if(ext_lnp){
		ext_lnp_num_cnt = cJSON_GetArraySize(ext_lnp);
	}

	_mrs_sys_datestring_sec(rdate);

	if(ext_num_cnt > 0 && ext_lnp_num_cnt > 0)
	{
		cJSON *arrexp=NULL, *arrexplnp=NULL;
		/* MPX_CustomProvNum Table에 INSERT */
		for(i=0; i < ext_num_cnt; i++)
		{
			arrexp 		= cJSON_GetArrayItem(ext, i);
			arrexplnp 	= cJSON_GetArrayItem(ext_lnp, i);
			if(!arrexp || !arrexplnp){
				/* 실패처리 */
				return ERR_INVALID_DATA;
			}
			if(compare_chr_len(strlen(arrexp->valuestring), sizeof(ext_num), "EXT_NUM", arrexp->valuestring)<0){return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(arrexplnp->valuestring), sizeof(ext_lnp_num), "EXT_LNP_NUM", arrexplnp->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(ext_num, 	arrexp->valuestring);
			strcpy(ext_lnp_num, arrexplnp->valuestring);

			nCount = 0;
			/*******************************************************************************************************************/
			/* EXT_NUM가 존재하는지 확인. */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					SELECT 	COUNT(*)
					INTO 	:nCount
					FROM 	MPX_CUSTOMPROVNUM
					WHERE 	CUST_ID = :cust_id AND EXT_NUM = :ext_num;
			/*******************************************************************************************************************/
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_cust] Select db(MPX_CUSTOMPROVNUM) Fail. - [%s][%d] %s\n",
						nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}

			if(nCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_cust] CUST_ID[%s] EXT_NUM[%s] already registered. - [%s][%d] %s\n",
						nid, cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_ALREADY_REGISTERD;
			}

			rep_flag=1;							/* 일반번호      */

			/*******************************************************************************************************************/
			/* INSERT 고객사 청약 번호 */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
						INSERT INTO MPX_CUSTOMPROVNUM
									(CUST_ID, SAID, EXT_NUM, EXT_LNP_NUM, REP_FLAG, NUM_TYPE, RDATE)
						VALUES		(:cust_id , :said , :ext_num , :ext_lnp_num , :rep_flag , :num_type , :rdate);
			/*******************************************************************************************************************/
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustomProvNum INSERT FAIL. EXT_NUM[%s], EXT_LNP_NUM[%s] - [%s][%d] %s\n",
					cust_id, ext_num, ext_lnp_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		}
	}
	else if(ext_num_cnt > 0)
	{
		cJSON *arrexp=NULL;
		/* MPX_CustomProvNum Table에 INSERT */
		for(i=0; i < ext_num_cnt; i++)
		{
			arrexp 		= cJSON_GetArrayItem(ext, i);
			if(!arrexp){
				/* 실패처리 */
				return ERR_INVALID_DATA;
			}
			if(compare_chr_len(strlen(arrexp->valuestring), sizeof(ext_num), "EXT_NUM", arrexp->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(ext_num, 	arrexp->valuestring);

			nCount = 0;
			/*******************************************************************************************************************/
			/* EXT_NUM가 존재하는지 확인. */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					SELECT 	COUNT(*)
					INTO 	:nCount
					FROM 	MPX_CUSTOMPROVNUM
					WHERE 	CUST_ID=:cust_id AND EXT_NUM=:ext_num;
			/*******************************************************************************************************************/
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_cust] Select db(MPX_CUSTOMPROVNUM) Fail. - [%s][%d] %s\n",
						nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}

			if(nCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_cust] CUST_ID[%s] EXT_NUM[%s] already registered. - [%s][%d] %s\n",
						nid, cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_ALREADY_REGISTERD;
			}

			rep_flag=1;							/* 일반번호      */

			/*******************************************************************************************************************/
			/* INSERT 고객사 청약 번호 */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
						INSERT INTO MPX_CUSTOMPROVNUM
									(CUST_ID, SAID, EXT_NUM, REP_FLAG, NUM_TYPE, RDATE)
						VALUES		(:cust_id, :said, :ext_num, :rep_flag, :num_type, :rdate);
			/*******************************************************************************************************************/
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustomProvNum INSERT FAIL. EXT_NUM[%s] - [%s][%d] %s\n",
					cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		}
	}
	else
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo EXT_NUM Count zero [%d]\n", ext_num_cnt);
		return ERR_INVALID_DATA;
	}

	rep_flag = 2; /* 대표번호 */
	/*******************************************************************************************************************/
	/* INSERT 고객사 청약 번호 */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
				INSERT INTO MPX_CUSTOMPROVNUM
							(CUST_ID, SAID, EXT_NUM, EXT_LNP_NUM, REP_FLAG, NUM_TYPE, RDATE)
				VALUES		(:cust_id , :said , :rep_num , :rep_lnp_num , :rep_flag , :num_type , :rdate);
	/*******************************************************************************************************************/
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		/*******************************************************************************************************************/
		/* MPX_CustomInfo Table에 UPDATE */
		/*******************************************************************************************************************/
		EXEC SQL AT :sessionid
				UPDATE 	MPX_CUSTOMPROVNUM
				SET 	REP_FLAG=:rep_flag
				WHERE 	CUST_ID=:cust_id AND EXT_NUM=:rep_num ;
		/*******************************************************************************************************************/
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, "MPX_CUSTOMPROVNUM UPDATE FAIL. CUST_ID[%s], REP_NUM[%s] - [%s][%d] %s\n", cust_id, rep_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		_mrs_logprint(DEBUG_5, "MPX_CUSTOMPROVNUM UPDATE SUCCESS. CUST_ID[%s], REP_NUM[%s] - [%s]\n", cust_id, rep_num, sessionid);
	}
	else
	{
		_mrs_logprint(DEBUG_5, "MPX_CUSTOMPROVNUM INSERT SUCCESS. CUST_ID[%s], REP_NUM[%s] - [%s]\n", cust_id, rep_num, sessionid);
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* mPBX 사업자 변경 */
/* 필수항목 정의 ---------------------------------------------
1. 모상품 계약 아이디(TOP_SA_ID)               - top_said,
2. mPBX 계약 고객사 고유번호(mPBX_CUST_ID)     - cust_id,
------------------------------------------------------------*/
int prov_proc_chg_cust(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		cust_status;
	int		max_user_num;
	int     nCount;

	char	said[24+1];
	char	cust_id[20+1];
	char	cust_name[100*2+1];
	char	num_type[30+1];
	char	rep_num[30+1];
	char	rep_lnp_num[24+1];
	char    ins_date[8+1];
	char    rdate[14+1];
	char	ext_num[24+1];
	char	ext_lnp_num[24+1];
	int		rep_flag;
	/* START, Ver 1.1.4, 2022.06.15 */
	int		svc_type;
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	int		i;
	int		ext_num_cnt=0;
	int		ext_lnp_num_cnt=0;
	int		len_name=0;
	char	*pchar;
	char    temp[256+1];
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;


	if(!root)
		return ERR_UNEXPECTED;

	memset(said,			0x00, sizeof(said));
	memset(cust_id,			0x00, sizeof(cust_id));
	memset(cust_name,		0x00, sizeof(cust_name));
	memset(num_type,		0x00, sizeof(num_type));
	memset(rep_num,			0x00, sizeof(rep_num));
	memset(rep_lnp_num,		0x00, sizeof(rep_lnp_num));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(rdate,			0x00, sizeof(rdate));
	memset(ext_num,			0x00, sizeof(ext_num));
	memset(ext_lnp_num,		0x00, sizeof(ext_lnp_num));
	memset(temp,			0x00, sizeof(temp));

	/* mPBX_CUST_ID */
	item = cJSON_GetObjectItem(root, "mPBX_CUST_ID");
	if(!item){ print_mis_para(__func__, "mPBX_CUST_ID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_id), "mPBX_CUST_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(cust_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_CUST_ID ---------------->[%s]\n", cust_id);
	item=NULL;

	/*******************************************************************************************************************/
	/* mPBX_CUST_ID가 존재하는지 확인. */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			SELECT 	COUNT(*)
			INTO 	:nCount
			FROM 	MPX_CUSTOMINFO
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	if (sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_chg_cust] Select db(MPX_CustomInfo) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_chg_cust] mPBX_CUST_ID[%s] empty. - [%s][%d] %s\n",
				nid, cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}

	/* START, Ver 1.1.4, 2022.06.15 add_svc_type, max_user_num */
	/*******************************************************************************************************************/
	/* UPDATE를 위해 기존 데이터를 가져온다. */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			SELECT 	CUST_ID, CUST_STATUS, CUST_NAME, SVC_TYPE, MPBX_USR_NUM
			INTO 	:cust_id, :cust_status, :cust_name, :svc_type, :max_user_num
			FROM 	MPX_CUSTOMINFO
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	/* END, Ver 1.1.4, 2022.06.15 add_svc_type, max_user_num */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CUSTOMINFO SELECT FAIL. CUST_ID[%s] - [%s][%d] %s\n", cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}
	_mrs_logprint(DEBUG_3, "(db-data) CUST_ID[%s], CUST_STATUS[%d], CUST_NAME[%s]\n", cust_id, cust_status, cust_name);

	/* SA_ID */
	item = cJSON_GetObjectItem(root, "SA_ID");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "SA_ID", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(said), "SA_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(said, item->valuestring);
		_mrs_logprint(DEBUG_5, "SA_ID ----------------------->[%s]\n", said);
		item=NULL;
	}

	/* CUST_NAME */
	item = cJSON_GetObjectItem(root, "CUST_NAME");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "CUST_NAME", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(cust_name), "CUST_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
		_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), cust_name, &len_name);
		//strcpy(cust_name, item->valuestring);
		_mrs_logprint(DEBUG_5, "CUST_NAME ------------------->[%s]\n", cust_name);
		item=NULL;
	}

	/* NUM_TYPE */
	item = cJSON_GetObjectItem(root, "NUM_TYPE");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "NUM_TYPE", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(num_type), "NUM_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(num_type, item->valuestring);
		_mrs_logprint(DEBUG_5, "NUM_TYPE -------------------->[%s]\n", num_type);
		item=NULL;
	}

	/* REP_NUM */
	item = cJSON_GetObjectItem(root, "REP_NUM");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "REP_NUM", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(rep_num), "REP_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(rep_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "REP_NUM --------------------->[%s]\n", rep_num);
		item=NULL;
	}

	/* REP_LNP_NUM */
	item = cJSON_GetObjectItem(root, "REP_LNP_NUM");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(rep_lnp_num), "REP_LNP_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(rep_lnp_num, item->valuestring);
		else rep_lnp_num[0] = 0x00;
		_mrs_logprint(DEBUG_5, "REP_LNP_NUM ----------------->[%s]\n", rep_lnp_num);
		item=NULL;
	}

	/* mPBX_STATE */
	item = cJSON_GetObjectItem(root, "mPBX_STATE");
	if(item){
		if(item->type == cJSON_Number) cust_status = item->valueint; else cust_status = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "CUST_STATUS ----------------->[%d]\n", cust_status);
		item=NULL;
	}

	/* START, Ver 1.1.4, add svc_type, max_user_num json */
	/* SVC_TYPE */
	item = cJSON_GetObjectItem(root, "SVC_TYPE");
	if(item){
		if(item->type == cJSON_Number) svc_type = item->valueint; else svc_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "SVC_TYPE ----------------->[%d]\n", svc_type);
		item=NULL;
	}


	/* MPBX_USR_NUM */
	item = cJSON_GetObjectItem(root, "MPBX_USR_NUM");
	if(item){
		if(item->type == cJSON_Number) max_user_num = item->valueint; else max_user_num = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MPBX_USR_NUM ----------------->[%d]\n", max_user_num);
		item=NULL;
	}
	/* END, Ver 1.1.4, add svc_type, max_user_num json */

	_mrs_sys_datestring_day(ins_date);

	/* START, Ver 1.1.4, add svc_type, max_user_num */
	/*******************************************************************************************************************/
	/* MPX_CustomInfo Table에 UPDATE */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			UPDATE 	MPX_CUSTOMINFO
			SET 	CUST_STATUS=:cust_status, CUST_NAME=:cust_name, UPDATE_DATE = TO_CHAR (SYSDATE, 'YYYYMMDDHHMISS'), SVC_TYPE =:svc_type, MPBX_USR_NUM = :max_user_num
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	/* END, Ver 1.1.4, add svc_type, max_user_num */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo UPDATE FAIL. CUST_ID[%s], SA_ID[%s] - [%s][%d] %s\n", cust_id, said, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	/* EXT_NUM */
	cJSON *ext = cJSON_GetObjectItem(root, "EXT_NUM");
	if(ext){
		ext_num_cnt = cJSON_GetArraySize(ext);
	}

	/* EXT_LNP_NUM */
	cJSON *ext_lnp = cJSON_GetObjectItem(root, "EXT_LNP_NUM");
	if(ext_lnp){
		ext_lnp_num_cnt = cJSON_GetArraySize(ext_lnp);
	}

	/*******************************************************************************************************************/
	/* 기존 고객사 청약 번호는 삭제 */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_CUSTOMPROVNUM
			WHERE 	CUST_ID = :cust_id AND SAID = :said;
	/*******************************************************************************************************************/
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA_FOUND)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustomProvNum[%s:%s] DELETE FAIL - [%s][%d] %s\n",
				cust_id, ext_num, ext_lnp_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	_mrs_sys_datestring_sec(rdate);

	if(ext_num_cnt > 0 && ext_lnp_num_cnt > 0)
	{

		cJSON *arrexp=NULL, *arrexplnp=NULL;

		/* EXT_NUM, EXT_LNP_NUM을 가지고, MPX_CustomProvNum Table에 INSERT하는 API 호출 */
		for(i=0; i < ext_num_cnt; i++)
		{
			arrexp 		= cJSON_GetArrayItem(ext, i);
			arrexplnp 	= cJSON_GetArrayItem(ext_lnp, i);
			if(!arrexp || !arrexplnp){
				/* 실패처리 */
				return ERR_INVALID_DATA;
			}
			if(compare_chr_len(strlen(arrexp->valuestring), sizeof(ext_num), "EXT_NUM", arrexp->valuestring)<0){return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(arrexplnp->valuestring), sizeof(ext_lnp_num), "EXT_LNP_NUM", arrexplnp->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(ext_num, 	arrexp->valuestring);
			strcpy(ext_lnp_num, arrexplnp->valuestring);

			nCount = 0;
			/*******************************************************************************************************************/
			/* EXT_NUM가 존재하는지 확인. */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					SELECT 	COUNT(*)
					INTO 	:nCount
					FROM 	MPX_CUSTOMPROVNUM
					WHERE 	CUST_ID=:cust_id AND EXT_NUM=:ext_num;
			/*******************************************************************************************************************/
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_cust] Select db(MPX_CUSTOMPROVNUM) Fail. - [%s][%d] %s\n",
						nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}

			if(nCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_cust] CUST_ID[%s] EXT_NUM[%s] already registered. - [%s][%d] %s\n",
						nid, cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_ALREADY_REGISTERD;
			}

			rep_flag=1;							/* 일반번호      */

			/*******************************************************************************************************************/
			/* INSERT 고객사 청약 번호 */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					INSERT INTO MPX_CUSTOMPROVNUM
							(CUST_ID, SAID, EXT_NUM, EXT_LNP_NUM, REP_FLAG, NUM_TYPE, RDATE)
					VALUES	(:cust_id, :said, :ext_num, :ext_lnp_num, :rep_flag, :num_type, :rdate);
			/*******************************************************************************************************************/
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustomProvNum[%s:%s] INSERT FAIL - [%s][%d] %s\n",
						cust_id, ext_num, ext_lnp_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
		}
	}
	else if(ext_num_cnt > 0)
	{
		cJSON *arrexp=NULL;
		/* MPX_CustomProvNum Table에 INSERT */
		for(i=0; i < ext_num_cnt; i++)
		{
			arrexp 		= cJSON_GetArrayItem(ext, i);
			if(!arrexp){
				/* 실패처리 */
				return ERR_INVALID_DATA;
			}
			if(compare_chr_len(strlen(arrexp->valuestring), sizeof(ext_num), "EXT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(ext_num, 	arrexp->valuestring);

			nCount = 0;
			/*******************************************************************************************************************/
			/* EXT_NUM가 존재하는지 확인. */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					SELECT 	COUNT(*)
					INTO 	:nCount
					FROM 	MPX_CUSTOMPROVNUM
					WHERE 	CUST_ID=:cust_id AND EXT_NUM=:ext_num;
			/*******************************************************************************************************************/
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_cust] Select db(MPX_CUSTOMPROVNUM) Fail. - [%s][%d] %s\n",
						nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}

			if(nCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_cust] CUST_ID[%s] EXT_NUM[%s] already registered. - [%s][%d] %s\n",
						nid, cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_ALREADY_REGISTERD;
			}

			rep_flag=1;							/* 일반번호      */

			/*******************************************************************************************************************/
			/* INSERT 고객사 청약 번호 */
			/*******************************************************************************************************************/
			EXEC SQL AT :sessionid
					INSERT INTO MPX_CUSTOMPROVNUM
							(CUST_ID, SAID, EXT_NUM, REP_FLAG, NUM_TYPE, RDATE)
					VALUES	(:cust_id, :said, :ext_num, :rep_flag, :num_type, :rdate);
			/*******************************************************************************************************************/
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustomProvNum INSERT FAIL. EXT_NUM[%s] - [%s][%d] %s\n",
					cust_id, ext_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		}
	}
	else
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo EXT_NUM Count zero [%d]\n", ext_num_cnt);
		return ERR_INVALID_DATA;
	}

	rep_flag = 2; /* 대표번호 */
	/*******************************************************************************************************************/
	/* INSERT 고객사 청약 번호 */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
				INSERT INTO MPX_CUSTOMPROVNUM
							(CUST_ID, SAID, EXT_NUM, EXT_LNP_NUM, REP_FLAG, NUM_TYPE, RDATE)
				VALUES		(:cust_id , :said , :rep_num , :rep_lnp_num , :rep_flag , :num_type , :rdate);
	/*******************************************************************************************************************/
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		/*******************************************************************************************************************/
		/* MPX_CustomInfo Table에 UPDATE */
		/*******************************************************************************************************************/
		EXEC SQL AT :sessionid
				UPDATE 	MPX_CUSTOMPROVNUM
				SET 	REP_FLAG=:rep_flag
				WHERE 	CUST_ID=:cust_id AND EXT_NUM=:rep_num ;
		/*******************************************************************************************************************/
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, "MPX_CUSTOMPROVNUM UPDATE FAIL. CUST_ID[%s], REP_NUM[%s] - [%s][%d] %s\n", cust_id, rep_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		_mrs_logprint(DEBUG_5, "MPX_CUSTOMPROVNUM UPDATE SUCCESS. CUST_ID[%s], REP_NUM[%s] - [%s]\n", cust_id, rep_num, sessionid);
	}
	else
	{
		_mrs_logprint(DEBUG_5, "MPX_CUSTOMPROVNUM INSERT SUCCESS. CUST_ID[%s], REP_NUM[%s] - [%s]\n", cust_id, rep_num, sessionid);
	}
	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* mPBX 사업자 삭제 */
/* 필수항목 정의 ---------------------------------------------
1. 모상품 계약 아이디(TOP_SA_ID)               - top_said,
------------------------------------------------------------*/
int prov_proc_del_cust(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	char	cust_id[24+1];
	char	biz_place_code[5+1];
	char	said[24+1];
	EXEC SQL END DECLARE SECTION;

	cJSON 	*item=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset(cust_id,			0x00, sizeof(cust_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(said,			0x00, sizeof(said));

	/* mPBX_CUST_ID */
	item = cJSON_GetObjectItem(root, "mPBX_CUST_ID");
	if(!item) { print_mis_para(__func__, "mPBX_CUST_ID"); goto no_required; }
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_id), "mPBX_CUST_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(cust_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_CUST_ID ---------------->[%s]\n", cust_id);
	item=NULL;

	/*******************************************************************************************************************/
	/* mPBX_CUST_ID가 존재하는지 확인. */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			SELECT 	COUNT(*)
			INTO 	:nCount
			FROM 	MPX_CUSTOMINFO
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_del_cust] Select db(MPX_CustomInfo) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	if(nCount == 0){
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_del_cust] mPBX_CUST_ID[%s] is empty. - [%s][%d] %s\n",
				nid, cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
//		return ERR_NOT_REGISTERED;
	}

	/* SA_ID */
	item = cJSON_GetObjectItem(root, "SA_ID");
	if(!item) { print_mis_para(__func__, "SA_ID"); goto no_required; }
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "SA_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(said), "SA_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(said, item->valuestring);
	_mrs_logprint(DEBUG_5, "SA_ID ----------------------->[%s]\n", said);
	item=NULL;

	/*******************************************************************************************************************/
	/* SAID 가 몇개 존재하는 지 확인하여 2개 이상일 경우                                                               */
	/* 삭제 요청된 SAID만 MPX_CUSTOMPROVNUM 테이블에서 삭제한 후 리턴하고 1개이면 고객사정보 모두 지운다.              */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			SELECT 	COUNT(DISTINCT SAID)
			INTO 	:nCount
			FROM 	MPX_CUSTOMPROVNUM
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_del_cust] Select db(MPX_CUSTOMPROVNUM) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount >= 2)
	{
		/*******************************************************************************************************************/
		/* 해당 SAID의 청약 번호 삭제 */
		/*******************************************************************************************************************/
		EXEC SQL AT :sessionid
				DELETE
				FROM 	MPX_CUSTOMPROVNUM
				WHERE 	CUST_ID = :cust_id AND SAID = :said;
		/*******************************************************************************************************************/
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2,
					"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTOMPROVNUM WHERE CUST_ID[%s] - [%d][%s]\n",
					nid, sessionid, cust_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			return ERR_DB_HANDLING;
		}
		else
		{
			_mrs_logprint(DEBUG_6,
					"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTOMPROVNUM WHERE CUST_ID[%s]\n",
					nid, sessionid, cust_id);
		}
		return PROV_SUCCESS;
	}

	/* 사용자 삭제 */
	EXEC SQL AT :sessionid DECLARE USER_INFO_CUR CURSOR FOR
	/*******************************************************************************************************************/
			SELECT 	BIZ_PLACE_CODE
			FROM 	MPX_BIZ_PLACE_INFO
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid OPEN USER_INFO_CUR;
	while(1)
	{
		biz_place_code[0] = '\0';
		EXEC SQL AT :sessionid FETCH USER_INFO_CUR INTO :biz_place_code;
		if(sqlca.sqlcode == SQL_SUCCESS)
		{
			EXEC SQL AT :sessionid
					DELETE
					FROM 	MPX_USERPROFILE
					WHERE 	BIZ_PLACE_CODE=:biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2,
						"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
						nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			else
			{
				_mrs_logprint(DEBUG_4,
						"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s]\n",
						nid, sessionid, biz_place_code);
			}

			/* START, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */			
			/* 사용자별 휴일관리 삭제 */
			EXEC SQL AT :sessionid
					DELETE
					FROM 	MPX_USER_HOLIDAY
					WHERE 	BIZ_PLACE_CODE=:biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2,
						"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_HOLIDAY WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
						nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				return ERR_DB_HANDLING;
			}
			else
			{
				_mrs_logprint(DEBUG_4,
						"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USER_HOLIDAY WHERE BIZ_PLACE_CODE[%s]\n",
						nid, sessionid, biz_place_code);
			}

			/* 사용자별 근무시간 삭제 */
			EXEC SQL AT :sessionid
					DELETE
					FROM 	MPX_USER_WORKTIME
					WHERE 	BIZ_PLACE_CODE=:biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2,
						"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
						nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				return ERR_DB_HANDLING;
			}
			else
			{
				_mrs_logprint(DEBUG_4,
						"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s]\n",
						nid, sessionid, biz_place_code);
			}
			/* END, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */

			/* 고객사별 휴일관리 삭제 */
			EXEC SQL AT :sessionid
					DELETE
					FROM 	MPX_CUSTHOLIDAY
					WHERE 	BIZ_PLACE_CODE=:biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2,
						"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
						nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				return ERR_DB_HANDLING;
			}
			else
			{
				_mrs_logprint(DEBUG_4,
						"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s]\n",
						nid, sessionid, biz_place_code);
			}

			/* 고객사별 근무시간 삭제 */
			EXEC SQL AT :sessionid
					DELETE
					FROM 	MPX_CUSTWORKTIME
					WHERE 	BIZ_PLACE_CODE=:biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2,
						"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTWORKTIME WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
						nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				return ERR_DB_HANDLING;
			}
			else
			{
				_mrs_logprint(DEBUG_4,
						"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTWORKTIME WHERE BIZ_PLACE_CODE[%s]\n",
						nid, sessionid, biz_place_code);
			}
		}
		else if(sqlca.sqlcode == SQL_NO_DATA)
		{
			break;
		}
		else
		{
			_mrs_logprint(DEBUG_2,
					" ??? nid[%d] s_id[%s] SELECT MPX_biz_place_info Fail. CUST_ID[%s] [%d]-[%s]\n",
					nid, sessionid, cust_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			//return ERR_DB_HANDLING;
		}
	}
	EXEC SQL AT :sessionid CLOSE USER_INFO_CUR;

	/* 사업장 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_BIZ_PLACE_INFO
			WHERE 	CUST_ID=:cust_id;
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_BIZ_PLACE_INFO WHERE CUST_ID[%s] - [%d][%s]\n",
				nid, sessionid, cust_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_BIZ_PLACE_INFO WHERE CUST_ID[%s]\n",
				nid, sessionid, cust_id);
	}

	/*******************************************************************************************************************/
	/* 청약 번호 삭제 */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_CUSTOMPROVNUM
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTOMPROVNUM WHERE CUST_ID[%s] - [%d][%s]\n",
				nid, sessionid, cust_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTOMPROVNUM WHERE CUST_ID[%s]\n",
				nid, sessionid, cust_id);
	}

	/*******************************************************************************************************************/
	/* 사업자 삭제 */
	/*******************************************************************************************************************/
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_CUSTOMINFO
			WHERE 	CUST_ID=:cust_id;
	/*******************************************************************************************************************/
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA) /* check sqlca.sqlcode */
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTOMINFO WHERE CUST_ID[%s] - [%d][%s]\n",
				nid, sessionid, cust_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	_mrs_logprint(DEBUG_6,
			"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTOMINFO WHERE CUST_ID[%s]\n",
			nid, sessionid, cust_id);

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* START, Ver 1.1.4, add user worktime check */
typedef struct _user_time_data{
	int		count;
	int 	wd[100];
	int 	st[100];
	int 	et[100];
} USER_TIME_DATA;

int _check_user_worktime_duplicate(cJSON *root)
{
	int i, j, ret, arr_cnt, wd, st, et;
	USER_TIME_DATA	utd;
	cJSON *subitem=NULL;
	cJSON *arritem=NULL;

	memset(&utd, 0x00, sizeof(USER_TIME_DATA));

	arr_cnt = cJSON_GetArraySize(root);
	for(i=0; i < arr_cnt; i++)
	{
		arritem = cJSON_GetArrayItem(root, i);

		/* WEEK_DAY */
		subitem = cJSON_GetObjectItem(arritem, "WD");
		if(subitem){
			if(subitem->type == cJSON_Number) wd = subitem->valueint; else wd = atoi(subitem->valuestring);
			subitem=NULL;
		}

		/* START_TIME */
		subitem = cJSON_GetObjectItem(arritem, "ST");
		if(subitem){
			st = atoi(subitem->valuestring);
			subitem=NULL;
		}

		/* END_TIME */
		subitem = cJSON_GetObjectItem(arritem, "ET");
		if(subitem){
			et = atoi(subitem->valuestring);
			subitem=NULL;
		}
		utd.count = arr_cnt;
		utd.wd[i] = wd;
		utd.st[i] = st;
		utd.et[i] = et;
	}

	if(arr_cnt == 0)
		return PROV_SUCCESS;

	for(i=0; i < utd.count; i++)
	{
		for(j=0; j < utd.count; j++)
		{
			if(i==j) continue;

			if(utd.wd[i] == utd.wd[j])
			{
				if((utd.st[i] >= utd.st[j] && utd.st[i] < utd.et[j]) ||
				(utd.et[i] > utd.st[j] && utd.et[i] <= utd.et[j])){
					_mrs_logprint(DEBUG_2, " MPX_USER_WorkTime Duplicated. WD[%d] - ST[%d], ET[%d] <> ST[%d], ET[%d]\n",
						utd.wd[i], utd.st[i], utd.et[i], utd.st[j], utd.et[j]);
					return ERR_INVALID_DATA;
				}
			}
		}
	}

	/* CHECK */
	return PROV_SUCCESS;
}
/* END, Ver 1.1.4, add user worktime check */

typedef struct _time_data{
	int		count;
	int 	type[100];
	int 	stime[100];
	int 	etime[100];
} TIME_DATA;

int _check_worktime_duplicate(cJSON *root)
{
	int i, j, ret, arr_cnt, day_type, stime, etime;
	TIME_DATA	td;
	cJSON *subitem=NULL;
	cJSON *arritem=NULL;

	memset(&td, 0x00, sizeof(TIME_DATA));

	arr_cnt = cJSON_GetArraySize(root);
	for(i=0; i < arr_cnt; i++)
	{
		arritem = cJSON_GetArrayItem(root, i);

		/* WEEK_DAY */
		subitem = cJSON_GetObjectItem(arritem, "WEEK_DAY");
		if(subitem){
			if(subitem->type == cJSON_Number) day_type = subitem->valueint; else day_type = atoi(subitem->valuestring);
			subitem=NULL;
		}

		/* START_TIME */
		subitem = cJSON_GetObjectItem(arritem, "START_TIME");
		if(subitem){
			stime = atoi(subitem->valuestring);
			subitem=NULL;
		}

		/* END_TIME */
		subitem = cJSON_GetObjectItem(arritem, "END_TIME");
		if(subitem){
			etime = atoi(subitem->valuestring);
			subitem=NULL;
		}
		td.count = arr_cnt;
		td.type[i] = day_type;
		td.stime[i] = stime;
		td.etime[i] = etime;
	}

	if(arr_cnt == 0)
		return PROV_SUCCESS;

	for(i=0; i < td.count; i++)
	{
		for(j=0; j < td.count; j++)
		{
			if(i==j) continue;

			if(td.type[i] == td.type[j])
			{
				if((td.stime[i] >= td.stime[j] && td.stime[i] < td.etime[j]) ||
				(td.etime[i] > td.stime[j] && td.etime[i] <= td.etime[j])){
					_mrs_logprint(DEBUG_2, " MPX_CustWorkTime Duplicated. DAY_TYPE[%d] - STIME[%d], ETIME[%d] <> SITME[%d], ETIME[%d]\n",
						td.type[i], td.stime[i], td.etime[i], td.stime[j], td.etime[j]);
					return ERR_INVALID_DATA;
				}
			}
		}
	}

	/* CHECK */
	return PROV_SUCCESS;
}

/* 사업장 정보 추가 */
/* 필수항목 정의 ---------------------------------------------
1. 사업장코드(BIZ_PLACE_CODE)                  - biz_place_code
2. 사업장 이름(BIZ_PLACE_NAM)                  - biz_place_name
3. 통화옵션 설정(CALL_OPT)                     - call_opt
4. 휴무일(CUST_HOLIDAY)                        - MPX_CustHoliday Table
5. 근무시간(CUST_WORKTIME)                     - MPX_CustWorkTime Table
------------------------------------------------------------*/
int prov_proc_add_biz_place(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	/* START, Ver 1.0.8, 2018.02.01 */
	//int		biz_place_accpfx;
	char	biz_place_accpfx[3+1];
	/* END, Ver 1.0.8, 2018.02.01 */
	int		out_pfx=0;
	int		pbx_port;
	int		m_type;
	int		day_type;
	int		repeat;
	int		pbx_flag=1;

	char	biz_place_code[5+1];
	char	cust_id[20+1];
	char	biz_place_name[100*2+1];
	char	mpbx_rn[24+1];
	char	account_num[24+1];
	char	pbx_ip[64+1];
	char	sub_svc[20+1];
	char	call_opt[20+1];
	char    ins_date[14+1];
	char    memo_title[14+1];
	char    h_day[8+1];
	char    stime[4+1];
	char    etime[4+1];
	char    rdate[8+1];
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	int		accpfx_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	/* START, Ver 1.1.4, 2022.06.15 */
	int		biz_svc_type;
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	int		i, arr_cnt=0, idx;
	int		len_place_name=0;
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(cust_id,			0x00, sizeof(cust_id));
	memset(biz_place_name,	0x00, sizeof(biz_place_name));
	memset(mpbx_rn,			0x00, sizeof(mpbx_rn));
	memset(account_num,		0x00, sizeof(account_num));
	memset(pbx_ip,			0x00, sizeof(pbx_ip));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(call_opt,		0x00, sizeof(call_opt));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(memo_title,		0x00, sizeof(memo_title));
	memset(h_day,			0x00, sizeof(h_day));
	memset(rdate,			0x00, sizeof(rdate));
	memset(stime,			0x00, sizeof(stime));
	memset(etime,			0x00, sizeof(etime));
	/* START, Ver 1.0.8, 2018.02.01 */
	memset(biz_place_accpfx,0x00, sizeof(biz_place_accpfx));
	/* END, Ver 1.0.8, 2018.02.01 */

	if(!root)
		return ERR_UNEXPECTED;

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
	_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(!strncmp(item->valuestring,"null",4))  return ERR_INVALID_DATA;
	_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_CODE", item->valuestring); return ERR_INVALID_DATA;}
	if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
	{
		_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
		return ERR_INVALID_DATA;
	}
	strcpy(biz_place_code, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
	item=NULL;

	/* CUST_ID */
	item = cJSON_GetObjectItem(root, "mPBX_CUST_ID");
	if(!item){ print_mis_para(__func__, "mPBX_CUST_ID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_id), "mPBX_CUST_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(cust_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_CUST_ID ---------------->[%s]\n", cust_id);
	item=NULL;

	/* 사업자가 등록되어 있는지 확인 */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_CUSTOMINFO WHERE CUST_ID=:cust_id;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_biz_place] Select db(MPX_CustomInfo) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_biz_place] %sMPBX_CUST_ID%s[%s]%s is empty in MPX_CustomInfo. - [%s][%d] %s\n",
				nid, SKY_COLOR, RED_COLOR, cust_id, NORMAL_COLOR, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}

	/* mPBX_CUST_ID가 존재하는지 확인. */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_biz_place] Select db(MPX_USERPROFILE) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount > 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_biz_place] CUST_ID[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
				nid, cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_ALREADY_REGISTERD;
	}

	/* BIZ_PLACE_NAME */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_NAME");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_NAME"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_NAME", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(biz_place_name), "BIZ_PLACE_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
	_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), biz_place_name, &len_place_name);
	//strcpy(biz_place_name, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_NAME -------------->[%s]\n", biz_place_name);
	item=NULL;

	/* MPBX_RN */
	item = cJSON_GetObjectItem(root, "MPBX_RN");
	if(!item){ print_mis_para(__func__, "MPBX_RN"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_RN", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(mpbx_rn), "MPBX_RN", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(mpbx_rn, item->valuestring);
	_mrs_logprint(DEBUG_5, "MPBX_RN --------------------->[%s]\n", mpbx_rn);
	item=NULL;

	/* MPBX_ACNT_NUM */
	item = cJSON_GetObjectItem(root, "MPBX_ACNT_NUM");
	if(!item){ print_mis_para(__func__, "MPBX_ACNT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_ACNT_NUM", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(account_num), "MPBX_ACNT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(account_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "MPBX_ACNT_NUM --------------->[%s]\n", account_num);
	item=NULL;

	/* BIZ_PLACE_ACCPFX */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_ACCPFX");
	if(item){
		/* START, Ver 1.0.8, 2018.02.01 */
		/*
		if(item->type == cJSON_Number) biz_place_accpfx = item->valueint; else biz_place_accpfx = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_ACCPFX ------------>[%d]\n", biz_place_accpfx);
		*/
		if(compare_chr_len(strlen(item->valuestring), sizeof(biz_place_accpfx), "BIZ_PLACE_ACCPFX", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(biz_place_accpfx, item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_ACCPFX ------------>[%s]\n", biz_place_accpfx);
		/* END, Ver 1.0.8, 2018.02.01 */
		item=NULL;
	}

	/* OUT_PFX */
	item = cJSON_GetObjectItem(root, "OUT_PFX");
	if(item){
		if(item->type == cJSON_Number) out_pfx = item->valueint; else out_pfx = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "OUT_PFX --------------------->[%d]\n", out_pfx);
		item=NULL;
	}

	/* PBX_IP */
	item = cJSON_GetObjectItem(root, "PBX_IP");
	if(!item){ print_mis_para(__func__, "PBX_IP"); goto no_required;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(pbx_ip), "PBX_IP", item->valuestring)<0){return ERR_INVALID_DATA;}
	if(strlen(item->valuestring) > 0)
		strcpy(pbx_ip, item->valuestring);
	else pbx_ip[0] = 0x00;
	_mrs_logprint(DEBUG_5, "PBX_IP ---------------------->[%s]\n", pbx_ip);
	item=NULL;

	/* PBX_PORT */
	item = cJSON_GetObjectItem(root, "PBX_PORT");
	if(!item){ print_mis_para(__func__, "PBX_PORT"); goto no_required;}
	if(item->type == cJSON_Number) pbx_port = item->valueint; else pbx_port = atoi(item->valuestring);
	_mrs_logprint(DEBUG_5, "PBX_PORT -------------------->[%d]\n", pbx_port);
	item=NULL;

	/* CALL_OPT */
	item = cJSON_GetObjectItem(root, "CALL_OPT");
	if(!item){ print_mis_para(__func__, "CALL_OPT"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "CALL_OPT", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(call_opt), "CALL_OPT", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(call_opt, item->valuestring);
	_mrs_logprint(DEBUG_5, "CALL_OPT -------------------->[%s]\n", call_opt);
	item=NULL;

	/* MPBX_SVC_LIST */
	item = cJSON_GetObjectItem(root, "MPBX_SVC_LIST");
	if(!item){ print_mis_para(__func__, "MPBX_SVC_LIST"); goto no_required;}
	arr_cnt = cJSON_GetArraySize(item);
	sprintf(sub_svc, "%020d", 0);	 // Initialize
	for(i=0; i < arr_cnt; i++)
	{
		arritem = cJSON_GetArrayItem(item, i);
		_mrs_logprint(DEBUG_5, "[%d] MPBX_SVC --------------->[%s]\n", i, arritem->valuestring);

		idx = _get_subsvc_index(arritem->valuestring);
		if(idx >= 0)
			sub_svc[idx]='1';
	}

	item=NULL;
	_mrs_logprint(DEBUG_5, "MPBX_SVC_LIST --------------->[%s]\n", sub_svc);

	/* PBX_FLAG */
	item = cJSON_GetObjectItem(root, "OFFICE_LINE_TYPE");
	if(item){
		if(item->type == cJSON_Number) pbx_flag = item->valueint; else pbx_flag = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "OFFICE_LINE_TYPE ------------>[%d]\n", pbx_flag);
		item=NULL;
	}

	/* MEMO_TITLE
	item = cJSON_GetObjectItem(root, "MEMO_TITLE");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(memo_title), "MEMO_TITLE", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(memo_title, item->valuestring);
		_mrs_logprint(DEBUG_5, "MEMO_TITLE=[%s]\n", memo_title);
		item=NULL;
	}
	*/

	/* START, Ver 1.0.8, 2018.02.01 */
	/* SHORT_NUM_LEN */
	item = cJSON_GetObjectItem(root, "SHORT_NUM_LEN");
	//if(!item){ print_mis_para(__func__, "SHORT_NUM_LEN"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) short_num_len = item->valueint; else short_num_len = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "SHORT_NUM_LEN ------------>[%d]\n", short_num_len);
		item=NULL;
	}else{
		short_num_len = 4;
	}

	/* ACCPFX_LEN */
	item = cJSON_GetObjectItem(root, "ACCPFX_LEN");
	//if(!item){ print_mis_para(__func__, "ACCPFX_LEN"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) accpfx_len = item->valueint; else accpfx_len = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "ACCPFX_LEN ------------>[%d]\n", accpfx_len);
		item=NULL;
	}else{
		accpfx_len = 2;
	}

	if(strlen(biz_place_accpfx) != accpfx_len){
		_mrs_logprint(DEBUG_5, "DIFF biz_place_accpfx/accpfx_len ------------>\n");
		return ERR_INVALID_DATA;
	}
	/* END, Ver 1.0.8, 2018.02.01 */

	/* START, Ver 1.1.4, add biz_svc_type json */
	/* BIZ_SVC_TYPE */
	item = cJSON_GetObjectItem(root, "BIZ_SVC_TYPE");
	//if(!item){ print_mis_para(__func__, "BIZ_SVC_TYPE"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) biz_svc_type = item->valueint; else biz_svc_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_SVC_TYPE ------------>[%d]\n", biz_svc_type);
		item=NULL;
	}else{
		biz_svc_type = 0;
	}
	/* END, Ver 1.1.4, add biz_svc_type json */

	/* MPX_BIZ_PLACE_INFO Table에 INSERT */
	/* START, Ver 1.1.4, add biz_svc_type */
	EXEC SQL AT :sessionid
		INSERT INTO MPX_BIZ_PLACE_INFO ( BIZ_PLACE_CODE, CUST_ID, BIZ_PLACE_NAM, MPBX_RN, ACCOUNT_NUM, BIZ_PLACE_ACCPFX, OUT_PFX,
			PBX_IP, PBX_PORT, SUBSVC, CALL_OPT, UPDATE_DATE, INS_DATE, PBX_FLAG, SHORTNUM_LEN, ACCPFX_LEN, BIZ_SVC_TYPE)
		VALUES( :biz_place_code, :cust_id, :biz_place_name, :mpbx_rn, :account_num, :biz_place_accpfx, :out_pfx,
			:pbx_ip, :pbx_port, :sub_svc, :call_opt, TO_CHAR (SYSDATE, 'YYYYMMDDHHMISS'), TO_CHAR (SYSDATE, 'YYYYMMDD'), :pbx_flag,
			:short_num_len, :accpfx_len, :biz_svc_type);
	/* END, Ver 1.1.4, add biz_svc_type */
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA) /* check sqlca.sqlcode */
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_BIZ_PLACE_INFO INSERT FAIL - [%s][%d] %s\n", sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		switch (sqlca.sqlcode)
		{
			case	-69720:		//data already exist.
				return ERR_ALREADY_REGISTERD;
			default :
				return ERR_DB_HANDLING;
		}
	}

	_mrs_sys_datestring_day(rdate);

	/* 고객사별 휴일 관리 */
	/* CUST_HOLIDAY */
	cJSON *holiday = cJSON_GetObjectItem(root, "CUST_HOLIDAY");
	if(holiday){
		arr_cnt = cJSON_GetArraySize(holiday);
		for(i=0; i < arr_cnt; i++)
		{
			arritem = cJSON_GetArrayItem(holiday, i);
			if(!arritem){ print_mis_para(__func__, "CUST_HOLIDAY"); goto no_required;}

			/* H_DAY */
			subitem = cJSON_GetObjectItem(arritem, "H_DAY");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - H_DAY"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(h_day), "H_DAY", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(h_day, subitem->valuestring);
			subitem=NULL;

			/* M_TYPE */
			subitem = cJSON_GetObjectItem(arritem, "M_TYPE");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - M_TYPE"); goto no_required;}
			if(subitem->type == cJSON_Number) m_type = subitem->valueint; else m_type = atoi(subitem->valuestring);
			subitem=NULL;

			/* REPEAT */
			subitem = cJSON_GetObjectItem(arritem, "REPEAT");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - REPEAT"); goto no_required;}
			if(subitem->type == cJSON_Number) repeat = subitem->valueint; else repeat = atoi(subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] H_DAY=[%s] : M_TYPE=[%d], REPEAT=[%d]\n", i, h_day, m_type, repeat);

			/* INSERT 고객사별 휴일 관리 */
			EXEC SQL AT :sessionid INSERT INTO MPX_CUSTHOLIDAY (BIZ_PLACE_CODE, CUST_HOLIDAY, M_TYPE, REPEAT, RDATE) VALUES(:biz_place_code, :h_day, :m_type, :repeat, :rdate);
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustHoliday INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
						cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		} // End of For
	}

	/* 고객사별 근무 시간 관리 */
	/* CUST_WORKTIME */
	cJSON *worktime = cJSON_GetObjectItem(root, "CUST_WORKTIME");
	if(worktime){
		/* Structure에 넣어서 중복 체크해야함 */
		if(_check_worktime_duplicate(worktime) != PROV_SUCCESS){ return ERR_INVALID_DATA; }

		arr_cnt = cJSON_GetArraySize(worktime);
		for(i=0; i < arr_cnt; i++)
		{
			arritem = cJSON_GetArrayItem(worktime, i);
			if(!arritem){ print_mis_para(__func__, "CUST_WORKTIME"); goto no_required;}

			/* WEEK_DAY */
			subitem = cJSON_GetObjectItem(arritem, "WEEK_DAY");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - WEEK_DAY"); goto no_required;}
			if(subitem->type == cJSON_Number) day_type = subitem->valueint; else day_type = atoi(subitem->valuestring);
			subitem=NULL;

			/* START_TIME */
			subitem = cJSON_GetObjectItem(arritem, "START_TIME");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - START_TIME"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(stime), "START_TIME", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(stime, subitem->valuestring);
			subitem=NULL;

			/* END_TIME */
			subitem = cJSON_GetObjectItem(arritem, "END_TIME");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - END_TIME"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(etime), "END_TIME", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(etime, subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] DAY_TYPE=[%d] : START_TIME[%s], END_TIME[%s]\n", i, day_type, stime, etime);

			/* INSERT 고객사별 근무시간 관리 */
			EXEC SQL AT :sessionid INSERT INTO MPX_CUSTWORKTIME (BIZ_PLACE_CODE, DAY_TYPE, STIME, ETIME, RDATE) VALUES(:biz_place_code, :day_type, :stime, :etime, :rdate);
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustWorkTime INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
						cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		} // End of For
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* 사업장 정보 변경 */
/* 필수항목 정의 ---------------------------------------------
1. 사업장코드(BIZ_PLACE_CODE)                  - biz_place_code
------------------------------------------------------------*/
int prov_proc_chg_biz_place(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	/* START, Ver 1.0.8, 2018.02.01 */
	//int		biz_place_accpfx;
	char	biz_place_accpfx[3+1];
	/* END, Ver 1.0.8, 2018.02.01 */
	int		out_pfx;
	int		pbx_port;
	int		m_type;
	int		day_type;
	int		repeat;
	int 	pbx_flag=1;

	char	biz_place_code[5+1];
	char	cust_id[20+1];
	char	biz_place_name[100*2+1];
	char	mpbx_rn[24+1];
	char	account_num[24+1];
	char	pbx_ip[64+1];
	char	sub_svc[20+1];
	char	call_opt[20+1];
	char    ins_date[14+1];
	char    memo_title[14+1];
	char    h_day[8+1];
	char    stime[4+1];
	char    etime[4+1];
	char    rdate[8+1];
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	int		accpfx_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	/* START, Ver 1.1.4, 2022.06.15 */
	int		biz_svc_type;
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	int		i, arr_cnt=0, idx;
	int		len_place_name=0;
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(cust_id,			0x00, sizeof(cust_id));
	memset(biz_place_name,	0x00, sizeof(biz_place_name));
	memset(mpbx_rn,			0x00, sizeof(mpbx_rn));
	memset(account_num,		0x00, sizeof(account_num));
	memset(pbx_ip,			0x00, sizeof(pbx_ip));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(call_opt,		0x00, sizeof(call_opt));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(memo_title,		0x00, sizeof(memo_title));
	memset(h_day,			0x00, sizeof(h_day));
	memset(rdate,			0x00, sizeof(rdate));
	memset(stime,			0x00, sizeof(stime));
	memset(etime,			0x00, sizeof(etime));
	/* START, Ver 1.0.8, 2018.02.01 */
	memset(biz_place_accpfx,	0x00, sizeof(biz_place_accpfx));
	/* END, Ver 1.0.8, 2018.02.01 */
	

	_mrs_logprint(DEBUG_3, "---------------- Change Biz Place Infomation ---------------\n");
	if(!root)
		return ERR_UNEXPECTED;

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
	_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(!strncmp(item->valuestring,"null",4))  return ERR_INVALID_DATA;
	_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
	{
		_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
		return ERR_INVALID_DATA;
	}
	strcpy(biz_place_code, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
	item=NULL;

	/* CUST_ID */
	item = cJSON_GetObjectItem(root, "mPBX_CUST_ID");
	if(!item){ print_mis_para(__func__, "mPBX_CUST_ID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(cust_id), "CUST_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(cust_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_CUST_ID ---------------->[%s]\n", cust_id);
	item=NULL;

	/* 사업자가 등록되어 있는지 확인 */
	EXEC SQL AT :sessionid 
			SELECT 	COUNT(*) 
			INTO 	:nCount 
			FROM 	MPX_CUSTOMINFO 
			WHERE 	CUST_ID=:cust_id;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_biz_place] Select db(MPX_CustomInfo) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_biz_place] MPBX_CUST_ID[%s] is empty in MPX_CustomInfo. - [%s][%d] %s\n",
				nid, cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}

	/* UPDATE를 위해 기존 데이터를 가져온다. */
	/* START, Ver 1.1.4, 2022.06.15 add biz_svc_type */
	/* START, Ver 1.0.8, 2018.02.01 */
	EXEC SQL AT :sessionid 
			SELECT 	NVL(BIZ_PLACE_NAM, ' '), MPBX_RN, ACCOUNT_NUM, BIZ_PLACE_ACCPFX, OUT_PFX, NVL(PBX_IP, ' '), PBX_PORT, SUBSVC, CALL_OPT, PBX_FLAG, SHORTNUM_LEN, ACCPFX_LEN, BIZ_SVC_TYPE
			INTO 	:biz_place_name, :mpbx_rn, :account_num, :biz_place_accpfx, :out_pfx, :pbx_ip, :pbx_port, :sub_svc, :call_opt, :pbx_flag, :short_num_len, :accpfx_len, :biz_svc_type
			FROM 	MPX_BIZ_PLACE_INFO 
			WHERE 	BIZ_PLACE_CODE=:biz_place_code AND 
					CUST_ID=:cust_id;
	/* END, Ver 1.0.8, 2018.02.01 */
	/* END, Ver 1.1.4, 2022.06.15 add biz_svc_type */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_BIZPLACEINFO SELECT FAIL. BIZ_PLACE_CODE[%s] CUST_ID[%s] - [%s][%d] %s\n", biz_place_code, cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}
	/* START, Ver 1.0.8, 2018.02.01 */
	//_mrs_logprint(DEBUG_3, "(db-data) BIZ_PLACE_NAME[%s], MPBX_RN[%s], ACCOUNT_NUM[%s], BIZ_PLACE_ACCPFX[%d]\n",biz_place_name,mpbx_rn,account_num,biz_place_accpfx);
	_mrs_logprint(DEBUG_3, "(db-data) BIZ_PLACE_NAME[%s], MPBX_RN[%s], ACCOUNT_NUM[%s], BIZ_PLACE_ACCPFX[%s]\n",biz_place_name,mpbx_rn,account_num,biz_place_accpfx);
	/* END, Ver 1.0.8, 2018.02.01 */
	_mrs_logprint(DEBUG_3, "(db-data) OUT_PFX[%d], PBX_IP[%s], PBX_PORT[%d], SUBSVC[%s], CALL_OPT[%s], PBX_FLAG[%d]\n",out_pfx,pbx_ip,pbx_port,sub_svc,call_opt,pbx_flag);
	_mrs_logprint(DEBUG_3, "(db-data) SHORTNUM_LEN[%d], ACCPFX_LEN[%d]\n",short_num_len,accpfx_len);

	/* BIZ_PLACE_NAME */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_NAME");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_NAME", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(biz_place_name), "BIZ_PLACE_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
		_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), biz_place_name, &len_place_name);
		//strcpy(biz_place_name, item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_NAME -------------->[%s]\n", biz_place_name);
		item=NULL;
	}

	/* MPBX_RN */
	item = cJSON_GetObjectItem(root, "MPBX_RN");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_RN", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(mpbx_rn), "MPBX_RN", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(mpbx_rn, item->valuestring);
		_mrs_logprint(DEBUG_5, "MPBX_RN --------------------->[%s]\n", mpbx_rn);
		item=NULL;
	}

	/* MPBX_ACNT_NUM */
	item = cJSON_GetObjectItem(root, "MPBX_ACNT_NUM");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_ACNT_NUM", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(account_num), "MPBX_ACNT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(account_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "MPBX_ACNT_NUM --------------->[%s]\n", account_num);
		item=NULL;
	}

	/* BIZ_PLACE_ACCPFX */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_ACCPFX");
	if(item){
		/* START, Ver 1.0.8, 2018.02.01 */
		/*
		if(item->type == cJSON_Number) biz_place_accpfx = item->valueint; else biz_place_accpfx = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_ACCPFX ------------>[%d]\n", biz_place_accpfx);
		*/
		if(compare_chr_len(strlen(item->valuestring), sizeof(biz_place_accpfx), "BIZ_PLACE_ACCPFX", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(biz_place_accpfx, item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_ACCPFX ------------>[%s]\n", biz_place_accpfx);
		/* END, Ver 1.0.8, 2018.02.01 */

		item=NULL;
	}

	/* OUT_PFX */
	item = cJSON_GetObjectItem(root, "OUT_PFX");
	if(item){
		if(item->type == cJSON_Number) out_pfx = item->valueint; else out_pfx = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "OUT_PFX --------------------->[%d]\n", out_pfx);
		item=NULL;
	}

	/* PBX_IP */
	item = cJSON_GetObjectItem(root, "PBX_IP");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(pbx_ip), "PBX_IP", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(pbx_ip, item->valuestring);
		else pbx_ip[0] = 0x00;
		_mrs_logprint(DEBUG_5, "PBX_IP ---------------------->[%s]\n", pbx_ip);
		item=NULL;
	}

	/* PBX_PORT */
	item = cJSON_GetObjectItem(root, "PBX_PORT");
	if(item){
		if(item->type == cJSON_Number) pbx_port = item->valueint; else pbx_port = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "PBX_PORT -------------------->[%d]\n", pbx_port);
		item=NULL;
	}

	/* CALL_OPT */
	item = cJSON_GetObjectItem(root, "CALL_OPT");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "CALL_OPT", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(call_opt), "CALL_OPT", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(call_opt, item->valuestring);
		_mrs_logprint(DEBUG_5, "CALL_OPT -------------------->[%s]\n", call_opt);
		item=NULL;
	}

	/* MPBX_SVC_LIST */
	item = cJSON_GetObjectItem(root, "MPBX_SVC_LIST");
	if(item){
		arr_cnt = cJSON_GetArraySize(item);
		sprintf(sub_svc, "%020d", 0);	 // Initialize
		for(i=0; i < arr_cnt; i++)
		{
			arritem = cJSON_GetArrayItem(item, i);
			_mrs_logprint(DEBUG_5, "[%d] MPBX_SVC_LIST ---------->[%s]\n", i, arritem->valuestring);
			idx = _get_subsvc_index(arritem->valuestring);
			if(idx >= 0)
				sub_svc[idx]='1';
			/* 일단 하드 코딩 */
			/*
			if(!strcasecmp(arritem->valuestring, "mPBX2010"))
				sub_svc[0]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2020"))
				sub_svc[1]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2030"))
				sub_svc[2]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2040"))
				sub_svc[3]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2050"))
				sub_svc[4]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2060"))
				sub_svc[5]='1';
			else if(!strcasecmp(arritem->valuestring, "mPBX2070"))
				sub_svc[6]='1';
			*/
		}
		item=NULL;
	}

	/* PBX_FLAG */
	item = cJSON_GetObjectItem(root, "OFFICE_LINE_TYPE");
	if(item){
		if(item->type == cJSON_Number) pbx_flag = item->valueint; else pbx_flag = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "OFFICE_LINE_TYPE ------------>[%d]\n", pbx_flag);
		item=NULL;
	}

	/* MEMO_TITLE
	item = cJSON_GetObjectItem(root, "MEMO_TITLE");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(memo_title), "MEMO_TITLE", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(memo_title, item->valuestring);
		_mrs_logprint(DEBUG_5, "MEMO_TITLE=[%s]\n", memo_title);
		item=NULL;
	}
	*/

	/* START, Ver 1.0.8, 2018.02.01 */
	/* SHORT_NUM_LEN */
	item = cJSON_GetObjectItem(root, "SHORT_NUM_LEN");
	//if(!item){ print_mis_para(__func__, "SHORT_NUM_LEN"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) short_num_len = item->valueint; else short_num_len = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "SHORT_NUM_LEN ------------>[%d]\n", short_num_len);
		item=NULL;
	}

	/* ACCPFX_LEN */
	item = cJSON_GetObjectItem(root, "ACCPFX_LEN");
	//if(!item){ print_mis_para(__func__, "ACCPFX_LEN"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) accpfx_len = item->valueint; else accpfx_len = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "ACCPFX_LEN ------------>[%d]\n", accpfx_len);
		item=NULL;
	}

	if(strlen(biz_place_accpfx) != accpfx_len){
		_mrs_logprint(DEBUG_5, "DIFF biz_place_accpfx/accpfx_len ------------>\n");
		return ERR_INVALID_DATA;
	}
	/* END, Ver 1.0.8, 2018.02.01 */

	/* START, Ver 1.1.4, add biz_svc_type json */
	/* BIZ_SVC_TYPE */
	item = cJSON_GetObjectItem(root, "BIZ_SVC_TYPE");
	//if(!item){ print_mis_para(__func__, "BIZ_SVC_TYPE"); goto no_required;}
	if(item){
		if(item->type == cJSON_Number) biz_svc_type = item->valueint; else biz_svc_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_SVC_TYPE ------------>[%d]\n", biz_svc_type);
		item=NULL;
	}
	/* END, Ver 1.1.4, add biz_svc_type json */

	/* MPX_BIZ_PLACE_INFO Table에 UPDATE */
	/* START, Ver 1.1.4, add biz_svc_type */
	EXEC SQL AT :sessionid
		UPDATE 	MPX_BIZ_PLACE_INFO 
		SET 	BIZ_PLACE_NAM=:biz_place_name, MPBX_RN=:mpbx_rn, ACCOUNT_NUM=:account_num, BIZ_PLACE_ACCPFX=:biz_place_accpfx,
				OUT_PFX=:out_pfx, PBX_IP=:pbx_ip, PBX_PORT=:pbx_port, SUBSVC=:sub_svc, CALL_OPT=:call_opt, 
				UPDATE_DATE = TO_CHAR (SYSDATE, 'YYYYMMDDHHMISS'), PBX_FLAG = :pbx_flag,
				SHORTNUM_LEN = :short_num_len, ACCPFX_LEN = :accpfx_len, BIZ_SVC_TYPE = :biz_svc_type
		WHERE 	BIZ_PLACE_CODE=:biz_place_code AND 
				CUST_ID=:cust_id;
	/* END, Ver 1.1.4, add biz_svc_type */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_BIZ_PLACE_INFO UPDATE FAIL - BIZ_PLACE_CODE[%s], CUST_ID[%s] %s\n", biz_place_code, cust_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	_mrs_sys_datestring_day(rdate);

	/* 고객사별 휴일 관리 */
	/* CUST_HOLIDAY */
	cJSON *holiday = cJSON_GetObjectItem(root, "CUST_HOLIDAY");
	if(holiday){
		/* 2016.06.17 instert data after delete */
		EXEC SQL AT :sessionid 
				DELETE 
				FROM 	MPX_CUSTHOLIDAY 
				WHERE 	BIZ_PLACE_CODE = :biz_place_code;
		if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA_FOUND)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustHoliday DELTE FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
				cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			/* 실패처리 */
			return ERR_DB_HANDLING;
		}

		arr_cnt = cJSON_GetArraySize(holiday);
		for(i=0; i < arr_cnt; i++)
		{
			arritem = cJSON_GetArrayItem(holiday, i);
			if(!arritem){ print_mis_para(__func__, "CUST_HOLIDAY"); goto no_required;}

			/* H_DAY */
			subitem = cJSON_GetObjectItem(arritem, "H_DAY");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - H_DAY"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(h_day), "H_DAY", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(h_day, subitem->valuestring);
			subitem=NULL;

			/* M_TYPE */
			subitem = cJSON_GetObjectItem(arritem, "M_TYPE");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - M_TYPE"); goto no_required;}
			if(subitem->type == cJSON_Number) m_type = subitem->valueint; else m_type = atoi(subitem->valuestring);
			subitem=NULL;

			/* REPEAT */
			subitem = cJSON_GetObjectItem(arritem, "REPEAT");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - REPEAT"); goto no_required;}
			if(subitem->type == cJSON_Number) repeat = subitem->valueint; else repeat = atoi(subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] H_DAY=[%s] : M_TYPE=[%d], REPEAT=[%d]\n", i, h_day, m_type, repeat);

			/* INSERT 고객사별 휴일 관리 */
			EXEC SQL AT :sessionid INSERT INTO MPX_CUSTHOLIDAY (BIZ_PLACE_CODE, CUST_HOLIDAY, M_TYPE, REPEAT, RDATE) VALUES(:biz_place_code, :h_day, :m_type, :repeat, :rdate);
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustHoliday INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
					cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		} // End of For
	}

	/* 고객사별 근무 시간 관리 */
	/* CUST_WORKTIME */
	cJSON *worktime = cJSON_GetObjectItem(root, "CUST_WORKTIME");

	if(worktime){
		/* Structure에 넣어서 중복 체크해야함 */
		if(_check_worktime_duplicate(worktime) != PROV_SUCCESS){ return ERR_INVALID_DATA; }

		/* 2016.06.17 insert after delete */
		EXEC SQL AT :sessionid 
				DELETE 
				FROM 	MPX_CUSTWORKTIME 
				WHERE 	BIZ_PLACE_CODE = :biz_place_code;
		if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA_FOUND)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustWorkTime DELTE FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
					cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			/* 실패처리 */
			return ERR_DB_HANDLING;
		}

		arr_cnt = cJSON_GetArraySize(worktime);
		for(i=0; i < arr_cnt; i++)
		{
			arritem = cJSON_GetArrayItem(worktime, i);
			if(!arritem){ print_mis_para(__func__, "CUST_WORKTIME"); goto no_required;}

			/* WEEK_DAY */
			subitem = cJSON_GetObjectItem(arritem, "WEEK_DAY");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - WEEK_DAY"); goto no_required;}
			if(subitem->type == cJSON_Number) day_type = subitem->valueint; else day_type = atoi(subitem->valuestring);
			subitem=NULL;

			/* START_TIME */
			subitem = cJSON_GetObjectItem(arritem, "START_TIME");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - START_TIME"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(stime), "START_TIME", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(stime, subitem->valuestring);
			subitem=NULL;

			/* END_TIME */
			subitem = cJSON_GetObjectItem(arritem, "END_TIME");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - END_TIME"); goto no_required;}
			if(compare_chr_len(strlen(subitem->valuestring), sizeof(etime), "END_TIME", subitem->valuestring)<0){return ERR_INVALID_DATA;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(etime, subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] DAY_TYPE=[%d] : START_TIME[%s], END_TIME[%s]\n", i, day_type, stime, etime);

			/* INSERT 고객사별 근무시간 관리 */
			EXEC SQL AT :sessionid 
					INSERT 
					INTO 	MPX_CUSTWORKTIME 
							(BIZ_PLACE_CODE, DAY_TYPE, STIME, ETIME, RDATE) 
					VALUES	(:biz_place_code, :day_type, :stime, :etime, :rdate);
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV[%s] - MPX_CustWorkTime INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
						cust_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}
		} // End of For
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* 사업장 정보 삭제 */
/* 필수항목 정의 ---------------------------------------------
1. 사업장코드(BIZ_PLACE_CODE)                  - biz_place_code
------------------------------------------------------------*/
int prov_proc_del_biz_place(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	char	biz_place_code[5+1];
	EXEC SQL END DECLARE SECTION;

	cJSON 	*item=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset(biz_place_code,	0x00, sizeof(biz_place_code));

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
	_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_CUST_ID", item->valuestring); return ERR_INVALID_DATA;}
	if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
	{
		_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
		return ERR_INVALID_DATA;
	}
	strcpy(biz_place_code, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
	item=NULL;

	/* BIZ_PLACE_CODE가 존재하는지 확인. */
	EXEC SQL AT :sessionid
			SELECT 	COUNT(*)
			INTO 	:nCount
			FROM 	MPX_BIZ_PLACE_INFO
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_del_biz_place] Select db(MPX_BIZ_PLACE_INFO) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_del_biz_place] BIZ_PLACE_CODE[%s] is empty. - [%s][%d] %s\n",
				nid, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		//return ERR_NOT_REGISTERED;
	}

	/* 가입자 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USERPROFILE
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	/* START, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */
	/* 사용자별 휴일관리 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USER_HOLIDAY
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_HOLIDAY WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_4,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	/* 사용자별 근무시간 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USER_WORKTIME
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_4,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}
	/* END, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */

	/* 고객사별 휴일관리 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_CUSTHOLIDAY
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	/* 고객사별 근무시간 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_CUSTWORKTIME
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_CUSTWORKTIME WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTWORKTIME WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	/* 사업장 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_BIZ_PLACE_INFO
			WHERE 	BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_6,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/************************************************************************************************************************
*  _remove_char 특정문자열 제거
************************************************************************************************************************/
void _remove_char(char *str, char digit)
{
	int len=strlen(str);
	int i=0,j=0;
	char tmp_str[64+1]="";

	for(i=0; i < len; i++)
	{
		if(str[i] == digit)
			continue;
		else
			tmp_str[j++] = str[i];
	}

	tmp_str[j]=0;

	memset(str, 0x00, sizeof(str));
	strcpy(str, tmp_str);
}

int _GNStoLNS(const char *gns, char *lns)
{
	int  strLen=0;
	int  telFlag = 0;
	char tmpGNS[70]="";
	char tmpLNS[70]="";
	char tmpTEL[70]="";

	/* Check gns length over 64 */
	strLen = strlen(gns);
	if(strLen > 64){
		strncpy(tmpTEL, gns, 64);
	}else{
		strcpy(tmpTEL, gns);
	}

	if(strncmp(tmpTEL, "tel:", 4) == 0){
		telFlag = 1;
		sprintf(tmpGNS, "%s", &tmpTEL[4]);
	}else{
		strcpy(tmpGNS, tmpTEL);
	}

	if(strncmp(tmpGNS, "+820", 4) == 0){
		sprintf(tmpLNS, "%s", &tmpGNS[3]);
	} else if(strncmp(tmpGNS, "+82", 3) == 0){
		sprintf(tmpLNS, "0%s", &tmpGNS[3]);
	} else if(strncmp(tmpGNS, "820", 3) == 0){
		sprintf(tmpLNS, "%s", &tmpGNS[2]);
	} else if(strncmp(tmpGNS, "82", 2) == 0){
		sprintf(tmpLNS, "0%s", &tmpGNS[2]);
	} else{
		strcpy(tmpLNS, tmpGNS);
	}

	if(telFlag == 1){
		sprintf(lns, "tel:%s", tmpLNS);
	}else{
		strcpy(lns, tmpLNS);
	}

	_mrs_logprint_thr(DEBUG_9, "(_GNStoLNS) Input:[%s] ---> Output:[%s]\n", gns, lns);
	return 1;
}

/* MOBILE_NUM에 대한 URI 확인 로직 */
/************************************************************************************************************************
 * _make_TEL_URI(char *ouri, char *curi);
 * 반드시 ouri는 MSISDN으로 반환되어야함.
 * curi 원래 URI
 ************************************************************************************************************************/
int _check_num_uri(char *ouri, char *curi)
{
	int 	i, uri_type=3;
	char    tmp_curi[124]="";

	/* COPY_ORIGINAL */
	strcpy(tmp_curi, ouri);

	if(!strncasecmp(ouri, "tel:", 4))
	{
		_GNStoLNS(&ouri[4], curi);
		// - 제거
		_remove_char(curi, '-');
		uri_type = 1;

	}
	else if(!strncasecmp(ouri, "sip:", 4))
	{
		strncpy(curi, &ouri[4],     strlen(ouri)-4);
		for(i=0; i < strlen(curi); i++)
		{
			if(curi[i] == '@')
				break;
		}
		curi[i] = 0x00;
		uri_type = 2;
	}
	else
	{
		_remove_char(ouri, '-');
		strcpy(curi, ouri);
	}

	if(uri_type != 2)
		_remove_char(tmp_curi, '-');

	strcpy(ouri, curi);
	strcpy(curi, tmp_curi);

	_mrs_logprint_thr(DEBUG_3, "(%s): MOBILE_NUM: %s, URI: %s, URI_TYPE[%s:%d\n", __func__,
		ouri, curi,
		uri_type == 1? "TEL_URI":
		uri_type == 2? "SIP_URI": "MSISDN",
		uri_type);
	return uri_type;
}


void chk_dev_type (char *devTypeList, int listLen, char inputDevType, int type) //type 1: add, 2: delete
{
	EXEC SQL BEGIN ARGUMENT SECTION;
		char *devTypeList;
	EXEC SQL END ARGUMENT SECTION;

	int i, j, pos, tmp, len;
	char	*ptr;

	/* list 값이랑 비교해서 없으면 넣자 */
	_mrs_logprint (DEBUG_9, "Device type list(%s), Input device type(%c) TYPE[%s]\n", devTypeList, inputDevType, 
		(type==1 ? "ADD" : "DELETE"));

	switch (type)
	{
		case	1:
			if (strchr (devTypeList, inputDevType) == NULL)
			{
					devTypeList[listLen] = inputDevType;
					listLen++;
			}
			break;
		case	2:
			if ((ptr = strchr (devTypeList, inputDevType)) != NULL)
			{
				len = strlen (ptr);
				for (i = 1; i < (len + 1); i++)
				{
					ptr[i - 1] = ptr[i];
				}
				ptr[len] = '\0';
				listLen--;
			}
			break;
	}

	/* 정렬 */
	for (i = 0; i < listLen; i++)
	{
		pos = i;

		for (j = i+1; j < listLen; j++)
		{
			if (devTypeList[pos] > devTypeList[j])
				pos = j;
		}

		tmp = devTypeList[i]; 
		devTypeList[i] = devTypeList[pos];
		devTypeList[pos] = tmp;
	}
	_mrs_logprint (DEBUG_9, "After sort, Device type list(%s), Input device type(%c)\n", devTypeList, inputDevType);
}

/* 사용자 정보 추가 - MPX_USERPROFILE TABLE */
/* 필수항목 정의 ---------------------------------------------
   ? TOP_SAID?
   1. 연락처 고유ID(USER_KEYID)
   2. mPBX 내선번호(mPBX_SHORT_NUM)
   3. 무선번호(mPBX_M_NUM)
   4. 모바일 이통사(mPBX_MSP)
   5. 유선전화번호(mPBX_EXT_NUM)
   6. mPBX 비밀번호(USER_PWD)
   7. mPBX통화방식(USER_mPBX_TYPE)
   8. 디바이스 OS 타입(DEV_OS)
   9. 디바이스 OS 버전(DEV_OS_VER)
   0. 사업장코드(BIZ_PLACE_CODE)
   ------------------------------------------------------------*/
int prov_proc_add_user(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	int		user_state;
	int		mpbx_type;			/* 1:VoLTE, 2:mVoIP, 3:ETC */
	int		mpbx_msp;			/* 1:SKT, 2:KT, 3:LTU+, 4:ETC, 9:유선만사용 */
	int		multi_dev_key = 0;	/* 1~5 */
	int		dev_type = 0;		/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os = 0;			/* 1:Android, 2:IOS, 3:Windows, 4:ETC */
	int		uri_type=3;			/* 1:TEL-URI, 2:SIP-URI, 3:MSISDN, 4:ETC */

	char	user_id[24+1];
	char	biz_place_code[5+1];
	char	short_num[7+1];
	char	m_num[24+1];
	char	ext_num[24+1];
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	//char	user_name[50*2+1];
	//char	user_email[50+1];
	char	user_name[150+1];
	char	user_email[300+1];
	char	position[60+1];
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	char	user_pwd[64+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char	sub_svc[20+1];
	char    ins_date[14+1];
	char	uri[64+1];
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	EXEC SQL END DECLARE SECTION;

	int		svc1=1,svc2=0,svc3=1,svc4=0,svc5=0,svc6=0, svc7=0;
	int		len_user_name=0;
	char	reason_text[50+1]="";
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;
	
	/* 2017.01.11 For checking valid variables */
	char	*ptr;
	long	value;

	if(!root)
		return ERR_UNEXPECTED;

	memset(user_id,			0x00, sizeof(user_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(short_num,		0x00, sizeof(short_num));
	memset(m_num,			0x00, sizeof(m_num));
	memset(ext_num,			0x00, sizeof(ext_num));
	memset(user_name,		0x00, sizeof(user_name));
	memset(user_email,		0x00, sizeof(user_email));
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(position,		0x00, sizeof(position));
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(user_pwd,		0x00, sizeof(user_pwd));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key, 		0x00, sizeof (push_key));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(uri,				0x00, sizeof(uri));
	

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
	_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(!strncmp(item->valuestring,"null",4))  return ERR_INVALID_DATA;

	_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);


	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_CODE", item->valuestring); return ERR_INVALID_DATA;}
	if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
	{
		_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
		return ERR_INVALID_DATA;
	}
	strcpy(biz_place_code, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
	item=NULL;

	/* 사업장이 등록되어 있는지 확인 */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] BIZ_PLACE_CODE[%s] is empty in MPX_biz_place_info. - [%s][%d] %s\n",
				nid, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}


	/* mPBX_CUST_ID가 존재하는지 확인. */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_USERPROFILE WHERE USER_ID=:user_id AND BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_USERPROFILE) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount > 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] USER_ID[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
				nid, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_ALREADY_REGISTERD;
	}

	/* USER_STATE */
	user_state = 1;			/* SET DEFAULT */
	item = cJSON_GetObjectItem(root, "USER_STATE");
	if(item){
		if(item->type == cJSON_Number) user_state = item->valueint; else user_state = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_STATE ------------------>[%d]\n", user_state);
		item=NULL;
	}

	/* mPBX_SHORT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_SHORT_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_SHORT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_SHORT_NUM", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(short_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_SHORT_NUM -------------->[%s]\n", short_num);
	item=NULL;

	/* START, Ver 1.0.8, 2018.02.01 */
	/* 내선번호 길이가 맞는지 확인 */
	EXEC SQL AT :sessionid SELECT SHORTNUM_LEN INTO :short_num_len FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(strlen(short_num) != short_num_len){
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] input_short_num_len[%d] db_short_num_len[%d] diff Fail. [%s]\n",
			nid, strlen(short_num), short_num_len, sessionid);
		return ERR_INVALID_DATA;
	}
	/* END, Ver 1.0.8, 2018.02.01 */

	/* mPBX_M_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_M_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_M_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_M_NUM", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(m_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_M_NUM ------------------>[%s]\n", m_num);
	item=NULL;

	/* Mobile번호에 대한 URI 체크 로직 */
	uri_type = _check_num_uri(m_num, uri);

	/* mPBX_MSP */
	item = cJSON_GetObjectItem(root, "mPBX_MSP");
	if(!item){ print_mis_para(__func__, "mPBX_MSP"); goto no_required;}
	if(item->type == cJSON_Number) mpbx_msp = item->valueint; else mpbx_msp = atoi(item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_MSP -------------------->[%d]\n", mpbx_msp);
	/* START, Ver 1.1.2, 2018.09.10 */
	//if(mpbx_msp < 1 || mpbx_msp > 4)
	if(mpbx_msp < 1 || mpbx_msp > 5)
	/* END, Ver 1.1.2, 2018.09.10 */
	{
		_mrs_logprint(DEBUG_5, "Invalid mPBX_MSP Value -------------------->[%d]\n", mpbx_msp);
		 return ERR_INVALID_DATA;
	}
	item=NULL;

	/* mPBX_EXT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_EXT_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_EXT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_EXT_NUM", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(ext_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_EXT_NUM ---------------->[%s]\n", ext_num);
	item=NULL;

	/* USER_NAME */
	item = cJSON_GetObjectItem(root, "USER_NAME");
	if(!item){ print_mis_para(__func__, "USER_NAME"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_NAME", item->valuestring); return ERR_INVALID_DATA;}
	_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), user_name, &len_user_name);
	//strcpy(user_name, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_NAME ------------------->[%s]\n", user_name);
	item=NULL;

	/* USER_EMAIL */
	item = cJSON_GetObjectItem(root, "USER_EMAIL");
	if(!item){ print_mis_para(__func__, "USER_EMAIL"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_EMAIL", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(user_email, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_EMAIL ------------------>[%s]\n", user_email);
	item=NULL;

	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/* POSITION */
	item = cJSON_GetObjectItem(root, "POSITION");
	if(!item){ print_mis_para(__func__, "POSITION"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "POSITION", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(position, item->valuestring);
	_mrs_logprint(DEBUG_5, "POSITION ------------------>[%s]\n", position);
	item=NULL;
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */

	/* USER_PWD */
#ifdef _NOT_USE_V0_98
	item = cJSON_GetObjectItem(root, "USER_PWD");
	if(item){
		strcpy(user_pwd, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_PWD=[%s]\n", user_pwd);
		item=NULL;
	}
#endif //_NOT_USE_V0_98

	/* USER_mPBX_TYPE */
	item = cJSON_GetObjectItem(root, "USER_mPBX_TYPE");
	if(!item){ print_mis_para(__func__, "USER_mPBX_TYPE"); goto no_required;}
	if(item->type == cJSON_Number) mpbx_type = item->valueint; 
	else 
	{
		value = strtol (item->valuestring, &ptr, 10);
		// 2017.01.11 "null" string으로 들어오는것에 대한 예외처리
		if (item->valuestring == ptr)
		{
			_mrs_logprint(DEBUG_2, "ERROR: USER_mPBX_TYPE VALUE[%S] isn't an integer \n", item->valuestring);
			return ERR_INVALID_DATA;
		}
		else
			mpbx_type = (int )value;
	}
	_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
	if(mpbx_type < 1 || mpbx_type > 3)
	{
		_mrs_logprint(DEBUG_5, "Invalid USER_mPBX_TYPE Value -------------------->[%d]\n", mpbx_type);
		 return ERR_INVALID_DATA;
	}
	item=NULL;

	/* MULTI_DEV_KEY */
	item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
	if(item){
		if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
		/* 2016.08.19 multi_dev_key must be always 1 */
		/* 20170110 multi_dev_key가 다른것도 올수 있음.
		if (multi_dev_key != 1)
		{
			_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
			return PROV_SUCCESS;
		}
		*/
		/* ---------------- */
		item=NULL;
	}
	else
	{
		/* 2016.09.21 우리는 1만 처리하니가.. multi_dev_key가 없으면 1로 처리 */
		multi_dev_key = 1;
	}


	/* DEV_TYPE */
	item = cJSON_GetObjectItem(root, "DEV_TYPE");
	if(item){
		if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
		item=NULL;
	}

	/* DEV_OS */
	item = cJSON_GetObjectItem(root, "DEV_OS");
	if(item){
		if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
		item=NULL;
	}

	/* DEV_OS_VER */
	item = cJSON_GetObjectItem(root, "DEV_OS_VER");
	if(item){
		strcpy(dev_os_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
		item=NULL;
	}

	/* DEV_APP_VER */
	item = cJSON_GetObjectItem(root, "DEV_APP_VER");
	if(item){
		strcpy(dev_app_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
		item=NULL;
	}

	/* DEV_MODEL */
	item = cJSON_GetObjectItem(root, "DEV_MODEL");
	if(item){
		strcpy(dev_model, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
		item=NULL;
	}

	/* DEV_ID */
	item = cJSON_GetObjectItem(root, "DEV_ID");
	if(item){
		strcpy(dev_id, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
		item=NULL;
	}

	/* PUSH_KEY */
	item = cJSON_GetObjectItem(root, "PUSH_KEY");
	if(item){
		strcpy(push_key, item->valuestring);
		_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
		item=NULL;
	}

	/* Set Default */
	_make_subsvc_str(sub_svc);

	/* SET USER_SUBSVC */
	/* MSP가 KT(2)인경우, 부가서비스 5,6번째 필드 설정 */
	if(mpbx_msp == MPX_MSP_KT){
		_set_subsvc_str(sub_svc, 5, 1);
		_set_subsvc_str(sub_svc, 6, 1);
	}

	/* START, Ver 1.1.2, 2018.09.10 */
	char cfg_status[1+1]="";
	int set_status =0;
	_get_cfg_subsvc_status(cfg_status);
	set_status = atoi(cfg_status);
	_mrs_logprint(DEBUG_5, "CFG SUBSVC STATUS -------------------->[%s][%d]\n", cfg_status,set_status);
	_set_subsvc_str(sub_svc, 9, set_status);
	/* END, Ver 1.1.2, 2018.09.10 */

	_mrs_sys_datestring_sec(ins_date);

	/* MPX_USERPROFILE Table에 INSERT */
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/*
	EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE
						( BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME,
						  USER_EMAIL, USER_PWD, USER_mPBX_TYPE, MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER,
						  DEV_APP_VER, DEV_MODEL, USER_SUBSVC, URI, URI_TYPE, INSERTDATE, UPDATEDATE)
			VALUES		( :biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name,
						  :user_email, :user_pwd, :mpbx_type, :multi_dev_key, :dev_type, :dev_os, :dev_os_ver,
						  :dev_app_ver, :dev_model, :sub_svc, :uri, :uri_type, :ins_date, :ins_date);
	*/
	EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE
						( BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME,
						  USER_EMAIL, POSITION, USER_PWD, USER_mPBX_TYPE, MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER,
						  DEV_APP_VER, DEV_MODEL, USER_SUBSVC, URI, URI_TYPE, INSERTDATE, UPDATEDATE)
			VALUES		( :biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name,
						  :user_email, :position, :user_pwd, :mpbx_type, :multi_dev_key, :dev_type, :dev_os, :dev_os_ver,
						  :dev_app_ver, :dev_model, :sub_svc, :uri, :uri_type, :ins_date, :ins_date);
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	if(SQLCODE == -69720)
	{
		_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Already exist. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_ALREADY_REGISTERD;

	}
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Fail. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	_mrs_logprint(DEBUG_5, "(%s) MPX_USERPROFILE Insert Success. USER_ID[%s], PLACE_CODE[%s] - [%s]\n",__func__, user_id, biz_place_code, sessionid);

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}


/* START, ver1.0.5 add */
/* 사용자 정보 추가 - MPX_USERPROFILE TABLE */
/* 필수항목 정의 ---------------------------------------------
   ? TOP_SAID?
   1. 연락처 고유ID(USER_KEYID)
   2. mPBX 내선번호(mPBX_SHORT_NUM)
   3. 무선번호(mPBX_M_NUM)
   4. 모바일 이통사(mPBX_MSP)
   5. 유선전화번호(mPBX_EXT_NUM)
   6. mPBX 비밀번호(USER_PWD)
   7. mPBX통화방식(USER_mPBX_TYPE)
   8. 디바이스 OS 타입(DEV_OS)
   9. 디바이스 OS 버전(DEV_OS_VER)
   10. 사업장코드(BIZ_PLACE_CODE)
   11. 사용자별 휴무일(MPX_USER_HOLIDAY)
   12. 사용자별 근무시간(MPX_USER_WORKTIME)
   ------------------------------------------------------------*/
int prov_proc_add_user_ref(int nid, char *sessionid, cJSON *root, char *ref, char *sup)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	int		dCount;
	int		user_state;
	int		mpbx_type;			/* 1:VoLTE, 2:mVoIP, 3:ETC */
	int		mpbx_msp;			/* 1:SKT, 2:KT, 3:LTU+, 4:ETC, 9:유선만사용 */
	int		multi_dev_key = 0;	/* 1~5 */
	int		dev_type = 0;		/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os = 0;			/* 1:Android, 2:IOS, 3:Windows, 4:ETC */
	int		uri_type=3;			/* 1:TEL-URI, 2:SIP-URI, 3:MSISDN, 4:ETC */

	char	user_id[24+1];
	char	biz_place_code[5+1];
	char	short_num[7+1];
	char	m_num[24+1];
	char	ext_num[24+1];
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	//char	user_name[50*2+1];
	//char	user_email[50+1];
	char	user_name[150+1];
	char	user_email[300+1];
	char	position[60+1];
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	char	user_pwd[64+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char	sub_svc[20+1];
	char    ins_date[14+1];
	char	uri[64+1];
	char	db_user_id[24+1];
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	/* START, Ver 1.1.4, 2022.06.15 */
	char	push_type[255+1];
	char	hd_stat[32+1];
	char	sh[8+1];
	char	eh[8+1];
	char	st[4+1];
	char	et[4+1];
	int		wd;
	char	rdate[8+1];
	EXEC SQL END DECLARE SECTION;

	int		i, arr_cnt=0, idx;
	/* END, Ver 1.1.4, 2022.06.15 */
	int		svc1=1,svc2=0,svc3=1,svc4=0,svc5=0,svc6=0, svc7=0;
	int		len_user_name=0;
	char	reason_text[50+1]="";
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;
	
	/* 2017.01.11 For checking valid variables */
	char	*ptr;
	long	value;

	if(!root)
		return ERR_UNEXPECTED;

	memset(user_id,			0x00, sizeof(user_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(short_num,		0x00, sizeof(short_num));
	memset(m_num,			0x00, sizeof(m_num));
	memset(ext_num,			0x00, sizeof(ext_num));
	memset(user_name,		0x00, sizeof(user_name));
	memset(user_email,		0x00, sizeof(user_email));
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(position,		0x00, sizeof(position));
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(user_pwd,		0x00, sizeof(user_pwd));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key, 		0x00, sizeof (push_key));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(uri,				0x00, sizeof(uri));
	memset(db_user_id,		0x00, sizeof(db_user_id));
	/* START, Ver 1.1.4, 2022.06.15 */
	memset(push_type,		0x00, sizeof(push_type));
	memset(hd_stat,		0x00, sizeof(hd_stat));	
	memset(sh,		0x00, sizeof(sh));
	memset(eh,		0x00, sizeof(eh));
	memset(st,		0x00, sizeof(st));
	memset(et,		0x00, sizeof(et));
	memset(rdate,		0x00, sizeof(rdate));
	/* END, Ver 1.1.4, 2022.06.15 */

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
	_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

	if(!strncmp(item->valuestring,"null",4))  return ERR_INVALID_DATA;

	_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);


	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_CODE", item->valuestring); return ERR_INVALID_DATA;}
	if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
	{
		_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
		return ERR_INVALID_DATA;
	}
	strcpy(biz_place_code, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
	item=NULL;

	/* 사업장이 등록되어 있는지 확인 */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount == 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] BIZ_PLACE_CODE[%s] is empty in MPX_biz_place_info. - [%s][%d] %s\n",
				nid, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}


	/* mPBX_CUST_ID가 존재하는지 확인. */
	EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_USERPROFILE WHERE USER_ID=:user_id AND BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select db(MPX_USERPROFILE) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(nCount > 0){
		/* ERROR 리턴 */
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
				nid, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		sprintf(ref,"USER_KEYID:%s",user_id);
		sprintf(sup,"%s","");

		return ERR_USER_KEY_ALREADY_REG;
		//return ERR_ALREADY_REGISTERD;
	}

	/* USER_STATE */
	user_state = 1;			/* SET DEFAULT */
	item = cJSON_GetObjectItem(root, "USER_STATE");
	if(item){
		if(item->type == cJSON_Number) user_state = item->valueint; else user_state = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_STATE ------------------>[%d]\n", user_state);
		item=NULL;
	}

	/* mPBX_SHORT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_SHORT_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_SHORT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_SHORT_NUM", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(short_num), "mPBX_SHORT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(short_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_SHORT_NUM -------------->[%s]\n", short_num);
	item=NULL;

	/* START, Ver 1.0.8, 2018.02.01 */
	/* 내선번호 길이가 맞는지 확인 */
	EXEC SQL AT :sessionid SELECT SHORTNUM_LEN INTO :short_num_len FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(strlen(short_num) != short_num_len){
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] input_short_num_len[%d] db_short_num_len[%d] diff Fail. [%s]\n",
			nid, strlen(short_num), short_num_len, sessionid);
		return ERR_INVALID_DATA;
	}
	/* END, Ver 1.0.8, 2018.02.01 */

	/* mPBX_M_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_M_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_M_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_M_NUM", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(m_num), "mPBX_M_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(m_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_M_NUM ------------------>[%s]\n", m_num);
	item=NULL;

	/* Mobile번호에 대한 URI 체크 로직 */
	uri_type = _check_num_uri(m_num, uri);

	/* mPBX_MSP */
	item = cJSON_GetObjectItem(root, "mPBX_MSP");
	if(!item){ print_mis_para(__func__, "mPBX_MSP"); goto no_required;}
	if(item->type == cJSON_Number) mpbx_msp = item->valueint; else mpbx_msp = atoi(item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_MSP -------------------->[%d]\n", mpbx_msp);
	/* START, Ver 1.1.2, 2018.09.10 */
	//if(mpbx_msp < 1 || mpbx_msp > 4)
	if(mpbx_msp < 1 || mpbx_msp > 5)
	/* END, Ver 1.1.2, 2018.09.10 */
	{
		_mrs_logprint(DEBUG_5, "Invalid mPBX_MSP Value -------------------->[%d]\n", mpbx_msp);
		 return ERR_INVALID_DATA;
	}
	item=NULL;

	/* mPBX_EXT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_EXT_NUM");
	if(!item){ print_mis_para(__func__, "mPBX_EXT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_EXT_NUM", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(ext_num), "mPBX_EXT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(ext_num, item->valuestring);
	_mrs_logprint(DEBUG_5, "mPBX_EXT_NUM ---------------->[%s]\n", ext_num);
	item=NULL;

	/* USER_NAME */
	item = cJSON_GetObjectItem(root, "USER_NAME");
	if(!item){ print_mis_para(__func__, "USER_NAME"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_NAME", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_name), "USER_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
	_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), user_name, &len_user_name);
	//strcpy(user_name, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_NAME ------------------->[%s]\n", user_name);
	item=NULL;

	/* USER_EMAIL */
	item = cJSON_GetObjectItem(root, "USER_EMAIL");
	if(!item){ print_mis_para(__func__, "USER_EMAIL"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_EMAIL", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_email), "USER_EMAIL", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_email, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_EMAIL ------------------>[%s]\n", user_email);
	item=NULL;

	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/* POSITION */
	item = cJSON_GetObjectItem(root, "POSITION");
	if(!item){ print_mis_para(__func__, "POSITION"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "POSITION", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(position), "POSITION", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(position, item->valuestring);
	_mrs_logprint(DEBUG_5, "POSITION ------------------>[%s]\n", position);
	item=NULL;
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */

	/* USER_PWD */
#ifdef _NOT_USE_V0_98
	item = cJSON_GetObjectItem(root, "USER_PWD");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(user_pwd), "USER_PWD", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(user_pwd, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_PWD=[%s]\n", user_pwd);
		item=NULL;
	}
#endif //_NOT_USE_V0_98

	/* USER_mPBX_TYPE */
	item = cJSON_GetObjectItem(root, "USER_mPBX_TYPE");
	if(!item){ print_mis_para(__func__, "USER_mPBX_TYPE"); goto no_required;}
	if(item->type == cJSON_Number) mpbx_type = item->valueint; 
	else 
	{
		value = strtol (item->valuestring, &ptr, 10);
		// 2017.01.11 "null" string으로 들어오는것에 대한 예외처리
		if (item->valuestring == ptr)
		{
			_mrs_logprint(DEBUG_2, "ERROR: USER_mPBX_TYPE VALUE[%S] isn't an integer \n", item->valuestring);
			return ERR_INVALID_DATA;
		}
		else
			mpbx_type = (int )value;
	}
	_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
	if(mpbx_type < 1 || mpbx_type > 3)
	{
		_mrs_logprint(DEBUG_5, "Invalid USER_mPBX_TYPE Value -------------------->[%d]\n", mpbx_type);
		 return ERR_INVALID_DATA;
	}
	item=NULL;

	/* MULTI_DEV_KEY */
	item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
	if(item){
		if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
		/* 2016.08.19 multi_dev_key must be always 1 */
		/* 20170110 multi_dev_key가 다른것도 올수 있음.
		if (multi_dev_key != 1)
		{
			_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
			return PROV_SUCCESS;
		}
		*/
		/* ---------------- */
		item=NULL;
	}
	else
	{
		/* 2016.09.21 우리는 1만 처리하니가.. multi_dev_key가 없으면 1로 처리 */
		multi_dev_key = 1;
	}


	/* DEV_TYPE */
	item = cJSON_GetObjectItem(root, "DEV_TYPE");
	if(item){
		if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
		item=NULL;
	}

	/* DEV_OS */
	item = cJSON_GetObjectItem(root, "DEV_OS");
	if(item){
		if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
		item=NULL;
	}

	/* DEV_OS_VER */
	item = cJSON_GetObjectItem(root, "DEV_OS_VER");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_os_ver), "DEV_OS_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(dev_os_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
		item=NULL;
	}

	/* DEV_APP_VER */
	item = cJSON_GetObjectItem(root, "DEV_APP_VER");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_app_ver), "DEV_APP_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(dev_app_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
		item=NULL;
	}

	/* DEV_MODEL */
	item = cJSON_GetObjectItem(root, "DEV_MODEL");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_model), "DEV_MODEL", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(dev_model, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
		item=NULL;
	}

	/* DEV_ID */
	item = cJSON_GetObjectItem(root, "DEV_ID");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_id), "DEV_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(dev_id, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
		item=NULL;
	}

	/* PUSH_KEY */
	item = cJSON_GetObjectItem(root, "PUSH_KEY");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(push_key), "PUSH_KEY", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(push_key, item->valuestring);
		_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
		item=NULL;
	}

	/* START, Ver 1.1.4, add push_type, hd_stat json */
	/* PUSH_TYPE */
	item = cJSON_GetObjectItem(root, "PUSH_TYPE");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(push_type), "PUSH_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(push_type, item->valuestring);
		_mrs_logprint(DEBUG_5, "PUSH_TYPE -------------------->[%s]\n", push_type);
		item=NULL;
	}

	/* HD_STAT */
	item = cJSON_GetObjectItem(root, "HD_STAT");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(hd_stat), "HD_STAT", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(hd_stat, item->valuestring);
		_mrs_logprint(DEBUG_5, "HD_STAT -------------------->[%s]\n", hd_stat);
		item=NULL;
	}
	/* END, Ver 1.1.4, add push_type, hd_stat json */

	/* Set Default */
	_make_subsvc_str(sub_svc);

	/* SET USER_SUBSVC */
	/* MSP가 KT(2)인경우, 부가서비스 5,6번째 필드 설정 */
	if(mpbx_msp == MPX_MSP_KT){
		_set_subsvc_str(sub_svc, 5, 1);
		_set_subsvc_str(sub_svc, 6, 1);
	}

	/* START, Ver 1.1.2, 2018.09.10 */
	char cfg_status[1+1]="";
	int set_status =0;
	_get_cfg_subsvc_status(cfg_status);
	set_status = atoi(cfg_status);
	_mrs_logprint(DEBUG_5, "CFG SUBSVC STATUS -------------------->[%s][%d]\n", cfg_status,set_status);
	_set_subsvc_str(sub_svc, 9, set_status);
	/* END, Ver 1.1.2, 2018.09.10 */

	_mrs_sys_datestring_sec(ins_date);

	/* 20170405, USER_NUM, EMAIL 은 위에서 미리 CHECK */
	/* USER_NUM */
	EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select ext_num&short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	if(dCount > 0)
	{
		/* USER_KEY */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] already registered. - [%s][%d] %s\n",
					nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",user_id);
			sprintf(sup,"%s","");

			return ERR_USER_KEY_ALREADY_REG;
		}

		/* M_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"mPBX_M_NUM:%s",m_num);

			return ERR_M_NUM_ALREADY_REG;
		}


		// ERROR 리턴 
		EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] EXT_NUM[%s] SHORT_NUM[%s] already registered. - [%s][%d] %s\n",
				nid, db_user_id, ext_num, short_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		sprintf(ref,"USER_KEYID:%s",db_user_id);
		sprintf(sup,"USER_NUM:%s,%s",ext_num,short_num);

		return ERR_USER_NUM_ALREADY_REG;
	}


	/* EMAIL */
	EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_EMAIL = :user_email;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select email db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	if(dCount > 0)
	{
		/* USER_KEY */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] already registered. - [%s][%d] %s\n",
					nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",user_id);
			sprintf(sup,"%s","");

			return ERR_USER_KEY_ALREADY_REG;
		}

		/* M_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"mPBX_M_NUM:%s",m_num);

			return ERR_M_NUM_ALREADY_REG;
		}

		/* SHORT_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] SHORT_NUM[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, short_num, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"mPBX_SHORT_NUM:%s",short_num);

			return ERR_SHORT_NUM_ALREADY_REG;
		}


		// ERROR 리턴
		EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where USER_EMAIL = :user_email;
		_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] USER_ID[%s] EMAIL[%s] already registered. - [%s][%d] %s\n",
				nid, db_user_id, user_email, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		sprintf(ref,"USER_KEYID:%s",db_user_id);
		sprintf(sup,"USER_EMAIL:%s",user_email);

		return ERR_EMAIL_ALREADY_REG;
	}


	/* MPX_USERPROFILE Table에 INSERT */
	/* START, Ver 1.1.4, 2022.06.15 add PUSH_TYPE, HD_STAT*/
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/*
	EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE
						( BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME,
						  USER_EMAIL, USER_PWD, USER_mPBX_TYPE, MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER,
						  DEV_APP_VER, DEV_MODEL, USER_SUBSVC, URI, URI_TYPE, INSERTDATE, UPDATEDATE)
			VALUES		( :biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name,
						  :user_email, :user_pwd, :mpbx_type, :multi_dev_key, :dev_type, :dev_os, :dev_os_ver,
						  :dev_app_ver, :dev_model, :sub_svc, :uri, :uri_type, :ins_date, :ins_date);
	*/
	EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE
						( BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME,
						  USER_EMAIL, POSITION, USER_PWD, USER_mPBX_TYPE, MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER,
						  DEV_APP_VER, DEV_MODEL, USER_SUBSVC, URI, URI_TYPE, INSERTDATE, UPDATEDATE, PUSH_TYPE, HD_STAT)
			VALUES		( :biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name,
						  :user_email, :position, :user_pwd, :mpbx_type, :multi_dev_key, :dev_type, :dev_os, :dev_os_ver,
						  :dev_app_ver, :dev_model, :sub_svc, :uri, :uri_type, :ins_date, :ins_date, :push_type, :hd_stat);
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	/* START, Ver 1.1.4, 2022.06.15 add PUSH_TYPE, HD_STAT*/
	if(SQLCODE == -69720)
	{
		_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Already exist. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		
		/* SEARCH DUPLICATED DATA */
		/* USER_KEY, SHORT_NUM, M_NUM, USER_NUM, EMAIL */

		/* USER_KEY */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] already registered. - [%s][%d] %s\n",
					nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",user_id);
			sprintf(sup,"%s","");

			return ERR_USER_KEY_ALREADY_REG;
		}

		/* M_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"mPBX_M_NUM:%s",m_num);

			return ERR_M_NUM_ALREADY_REG;
		}

		/* SHORT_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0){
			/* ERROR 리턴 */
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] SHORT_NUM[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, short_num, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"mPBX_SHORT_NUM:%s",short_num);

			return ERR_SHORT_NUM_ALREADY_REG;
		}

		/* USER_NUM */
		/* 위에서 CHECK : 20170404
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
		if(dCount > 0){
			// ERROR 리턴 
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] EXT_NUM[%s] SHORT_NUM[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, ext_num, short_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"USER_NUM:%s",ext_num);

			return ERR_USER_NUM_ALREADY_REG;
		}
		*/

		/* EMAIL */
		/* 위에서 CHECK : 20170404
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_EMAIL = :user_email;
		if(dCount > 0){
			// ERROR 리턴
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] USER_ID[%s] EMAIL[%s] already registered. - [%s][%d] %s\n",
					nid, user_id, user_email, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",user_id);
			sprintf(sup,"USER_EMAIL:%s",user_email);

			return ERR_EMAIL_ALREADY_REG;
		}
		*/

		return ERR_ALREADY_REGISTERD;

	}
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Fail. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	_mrs_logprint(DEBUG_5, "(%s) MPX_USERPROFILE Insert Success. USER_ID[%s], PLACE_CODE[%s] - [%s]\n",__func__, user_id, biz_place_code, sessionid);
	
	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}
/* END, ver1.0.5 add */

/* 사용자 정보 변경 */
int prov_proc_chg_user(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount, cnt;
	int		user_state;
	int		mpbx_type;			/* 1:VoLTE, 2:mVoIP, 3:ETC */
	int		mpbx_msp;			/* 1:SKT, 2:KT, 3:LTU+, 4:ETC, 9:유선만사용 */
	int		multi_dev_key;		/* 1~5 */
	int		dev_type;			/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os;				/* 1:Android, 2:IOS, 3:Windows, 4:ETC */
	int		uri_type=3;			/* 1:TEL-URI, 2:SIP-URI, 3:MSISDN, 4:ETC */

	char	user_id[24+1];
	char	biz_place_code[5+1];
	char	short_num[7+1];
	char	m_num[24+1];
	char	ext_num[24+1];
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	//char	user_name[50*2+1];
	//char	user_email[50+1];
	char	user_name[150+1];
	char	user_email[300+1];
	char	position[60+1];
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	char	user_pwd[64+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char	sub_svc[20+1];
	char    ins_date[14+1];
	char    chg_date[14+1];
	int		cf_type;
	char	cf_telno[24+1];
	char	uri[64+1];
	int		user_type;
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	EXEC SQL END DECLARE SECTION;

	int		len_user_name=0;
	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset(user_id,			0x00, sizeof(user_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(short_num,		0x00, sizeof(short_num));
	memset(m_num,			0x00, sizeof(m_num));
	memset(ext_num,			0x00, sizeof(ext_num));
	memset(user_name,		0x00, sizeof(user_name));
	memset(user_email,		0x00, sizeof(user_email));
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(position,		0x00, sizeof(position));
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(user_pwd,		0x00, sizeof(user_pwd));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key,		0x00, sizeof (push_key));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(chg_date,		0x00, sizeof(chg_date));
	memset(cf_telno,		0x00, sizeof(cf_telno));
	memset(uri,				0x00, sizeof(uri));

	cf_type					= 0;
	user_type				= 0;

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;


	/* UPDATE를 위해 기존 데이터를 가져온다. */
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/*
	EXEC SQL AT :sessionid SELECT SHORT_NUM, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_mPBX_TYPE, 
		MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, URI_TYPE, URI, DEV_ID, PUSH_KEY, BIZ_PLACE_CODE,
		USER_TYPE
	INTO :short_num, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, :multi_dev_key, 
		:dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :uri_type, :uri, :dev_id, :push_key, :biz_place_code,
		:user_type
	FROM MPX_USERPROFILE 
	WHERE USER_ID=:user_id;
	*/
	EXEC SQL AT :sessionid SELECT SHORT_NUM, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_mPBX_TYPE, 
		MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, URI_TYPE, URI, DEV_ID, PUSH_KEY, BIZ_PLACE_CODE,
		USER_TYPE, POSITION
	INTO :short_num, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, :multi_dev_key, 
		:dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :uri_type, :uri, :dev_id, :push_key, :biz_place_code,
		:user_type, :position
	FROM MPX_USERPROFILE 
	WHERE USER_ID=:user_id;
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */

	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo SELECT FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}

	/* BIZ_PLACE_CODE */
	item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
	if (item)
	{
		if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
		{
			_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
			return ERR_MISSING_PARAMETER;
		}
		strcpy(biz_place_code, item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);

		/* biz_place_code 변경.... 해당 사업장 존재하는지 확인 */
		EXEC SQL AT :sessionid
			SELECT COUNT(*) INTO :cnt FROM MPX_BIZ_PLACE_INFO 
			WHERE BIZ_PLACE_CODE = :biz_place_code;
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_BIZ_PLACE_INFO SELECT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n", biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		/* biz not exist */
		if (cnt == 0)
		{
			_mrs_logprint_thr (DEBUG_1, "BIZ NOT EXIST. BIZ_PLACE_CODE[%s]\n", biz_place_code);
			return ERR_NOT_REGISTERED;
		}
	}
	item=NULL;

	/* USER_STATE */
	user_state = 1;			/* SET DEFAULT */
	item = cJSON_GetObjectItem(root, "USER_STATE");
	if(item){
		if(item->type == cJSON_Number) user_state = item->valueint; else user_state = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_STATE ------------------>[%d]\n", user_state);
		item=NULL;
	}

	/* mPBX_SHORT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_SHORT_NUM");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_SHORT_NUM", item->valuestring); return ERR_INVALID_DATA;}
		strcpy(short_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_SHORT_NUM -------------->[%s]\n", short_num);
		item=NULL;
	}

	/* START, Ver 1.0.8, 2018.02.01 */
	/* 내선번호 길이가 맞는지 확인 */
	EXEC SQL AT :sessionid SELECT SHORTNUM_LEN INTO :short_num_len FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	if(strlen(short_num) != short_num_len){
		_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] input_short_num_len[%d] db_short_num_len[%d] diff Fail. [%s]\n",
			nid, strlen(short_num), short_num_len, sessionid);
		return ERR_INVALID_DATA;
	}
	/* END, Ver 1.0.8, 2018.02.01 */

	/* mPBX_M_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_M_NUM");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_M_NUM", item->valuestring); return ERR_INVALID_DATA;}
		strcpy(m_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_M_NUM ------------------>[%s]\n", m_num);
		item=NULL;

		/* Mobile번호에 대한 URI 체크 로직 */
		uri_type = _check_num_uri(m_num, uri);
	}

	/* mPBX_MSP */
	item = cJSON_GetObjectItem(root, "mPBX_MSP");
	if(item){
		if(item->type == cJSON_Number) mpbx_msp = item->valueint; else mpbx_msp = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_MSP -------------------->[%d]\n", mpbx_msp);
		/* START, Ver 1.1.2, 2018.09.10 */
		//if(mpbx_msp < 1 || mpbx_msp > 4) 
		if(mpbx_msp < 1 || mpbx_msp > 5) 
		/* END, Ver 1.1.2, 2018.09.10 */
		{
			_mrs_logprint(DEBUG_5, "Invalid mPBX_MSP Value -------------------->[%d]\n", mpbx_msp);
			 return ERR_INVALID_DATA;
		}
		item=NULL;
	}

	/* mPBX_EXT_NUM */
	item = cJSON_GetObjectItem(root, "mPBX_EXT_NUM");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_EXT_NUM", item->valuestring); return ERR_INVALID_DATA;}
		strcpy(ext_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_EXT_NUM ---------------->[%s]\n", ext_num);
		item=NULL;
	}

	/* USER_NAME */
	item = cJSON_GetObjectItem(root, "USER_NAME");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_NAME", item->valuestring); return ERR_INVALID_DATA;}
		_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), user_name, &len_user_name);
		//strcpy(user_name, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_NAME ------------------->[%s]\n", user_name);
		item=NULL;
	}

	/* USER_EMAIL */
	item = cJSON_GetObjectItem(root, "USER_EMAIL");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_EMAIL", item->valuestring); return ERR_INVALID_DATA;}
		strcpy(user_email, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_EMAIL ------------------>[%s]\n", user_email);
		item=NULL;
	}

	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/* POSITION */
	item = cJSON_GetObjectItem(root, "POSITION");
	if(item){
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "POSITION", item->valuestring); return ERR_INVALID_DATA;}
		strcpy(position, item->valuestring);
		_mrs_logprint(DEBUG_5, "POSITION ------------------>[%s]\n", position);
		item=NULL;
	}
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */

#ifdef _NOT_USE_V0_99
	/* USER_PWD */
	item = cJSON_GetObjectItem(root, "USER_PWD");
	if(item){
		strcpy(user_pwd, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_PWD=[%s]\n", user_pwd);
		item=NULL;
	}
#endif //_NOT_USE_V0_99

	/* DEV_ID */
	item = cJSON_GetObjectItem(root, "DEV_ID");
	if(item){
		strcpy(dev_id, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
		item=NULL;
	}

	/* PUSH_KEY */
	item = cJSON_GetObjectItem(root, "PUSH_KEY");
	if(item){
		strcpy(push_key, item->valuestring);
		_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
		item=NULL;
	}

	/* USER_mPBX_TYPE */
	item = cJSON_GetObjectItem(root, "USER_mPBX_TYPE");
	if(item){
		if(item->type == cJSON_Number) mpbx_type = item->valueint; else mpbx_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
		item=NULL;
	}

	/* MULTI_DEV_KEY */
	item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
	if(item){
		if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
		/* 2016.08.19 multi_dev_key must be always 1 */
		/* 20170110 multi_dev_key가 다른것도 올수 있음. v1.0.5 add
		if (multi_dev_key != 1)
		{
			_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
			return PROV_SUCCESS;
		}
		*/
		/* ---------------- */
		item=NULL;
	}

	/* DEV_TYPE */
	item = cJSON_GetObjectItem(root, "DEV_TYPE");
	if(item){
		if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
		item=NULL;
	}

	/* DEV_OS */
	item = cJSON_GetObjectItem(root, "DEV_OS");
	if(item){
		if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
		item=NULL;
	}

	/* DEV_OS_VER */
	item = cJSON_GetObjectItem(root, "DEV_OS_VER");
	if(item){
		strcpy(dev_os_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
		item=NULL;
	}

	/* DEV_APP_VER */
	item = cJSON_GetObjectItem(root, "DEV_APP_VER");
	if(item){
		strcpy(dev_app_ver, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
		item=NULL;
	}

	/* DEV_MODEL */
	item = cJSON_GetObjectItem(root, "DEV_MODEL");
	if(item){
		strcpy(dev_model, item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
		item=NULL;
	}

	/* 08.19 ,   ygkim
	if ( (dev_type != 0 || dev_os != 0 || strlen (dev_os_ver) > 0 
		|| strlen (dev_app_ver) > 0 || strlen (dev_model) > 0) && multi_dev_key == 0)
	{
		_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY is missed for updating Device info.\n");
		goto no_required;
	}
	*/

	_mrs_sys_datestring_sec(chg_date);

	/* Insert MPX_USERPROFILE Table after deleted table */
	/* Delete */
	EXEC SQL AT :sessionid DELETE FROM MPX_USERPROFILE WHERE USER_ID = :user_id;
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo DELETE FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	/* INSERT */
	/*
	_mrs_logprint (DEBUG_9, "--- INSERT VALUE\n");
	_mrs_logprint (DEBUG_9, "--- BIZ_PLACE_CODE: %100s\n", biz_place_code);
	_mrs_logprint (DEBUG_9, "--- SHORT_NUM: %100s\n", short_num);
	_mrs_logprint (DEBUG_9, "--- USER_ID: %100s\n", user_id);
	_mrs_logprint (DEBUG_9, "--- USER_STATUS: %100d\n", user_state);
	_mrs_logprint (DEBUG_9, "--- MOBILE_NUM: %100s\n", m_num);
	_mrs_logprint (DEBUG_9, "--- MSP: %100d\n", mpbx_msp);
	_mrs_logprint (DEBUG_9, "--- EXT_NUM: %100s\n", ext_num);
	_mrs_logprint (DEBUG_9, "--- USER_NAME: %100s\n", user_name);
	_mrs_logprint (DEBUG_9, "--- USER_EMAIL: %100s\n", user_email);
	_mrs_logprint (DEBUG_9, "--- USER_MPBX_TYPE: %100d\n", mpbx_type);
	_mrs_logprint (DEBUG_9, "--- MULTI_DEV_KEY: %100d\n", multi_dev_key);
	_mrs_logprint (DEBUG_9, "--- DEV_TYPE: %100d\n", dev_type);
	_mrs_logprint (DEBUG_9, "--- DEV_OS: %100d\n", dev_os);
	_mrs_logprint (DEBUG_9, "--- DEV_OS_VER: %100s\n", dev_os_ver);
	_mrs_logprint (DEBUG_9, "--- DEV_APP_VER: %100s\n", dev_app_ver);
	_mrs_logprint (DEBUG_9, "--- DEV_MODEL: %100s\n", dev_model);
	_mrs_logprint (DEBUG_9, "--- USER_SUBSVC: %100s\n", sub_svc);
	_mrs_logprint (DEBUG_9, "--- CF_TYPE: %100d\n", cf_type);
	_mrs_logprint (DEBUG_9, "--- CF_TELNO: %100s\n", cf_telno);
	_mrs_logprint (DEBUG_9, "--- INSERTDATE: %100s\n", ins_date);
	_mrs_logprint (DEBUG_9, "--- UPDATEDATE: %100s\n", chg_date);
	_mrs_logprint (DEBUG_9, "--- URI: %100s\n", uri);
	_mrs_logprint (DEBUG_9, "--- URI_TYPE: %100d\n", uri_type);
	_mrs_logprint (DEBUG_9, "--- DEV_ID: %100s\n", dev_id);
	_mrs_logprint (DEBUG_9, "--- PUSH_KEY: %100s\n", push_key);
	_mrs_logprint (DEBUG_9, "--- USER_TYPE: %100d\n", user_type);
	*/

	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	/*
	EXEC SQL AT :sessionid
		INSERT INTO MPX_USERPROFILE (BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_MPBX_TYPE, 
			MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, UPDATEDATE, 
			URI, URI_TYPE, DEV_ID, PUSH_KEY, USER_TYPE)
		VALUES (:biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, 
			:multi_dev_key, :dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :chg_date,
			:uri, :uri_type, :dev_id, :push_key, :user_type);
	*/
	EXEC SQL AT :sessionid
		INSERT INTO MPX_USERPROFILE (BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_MPBX_TYPE, 
			MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, UPDATEDATE, 
			URI, URI_TYPE, DEV_ID, PUSH_KEY, USER_TYPE, POSITION)
		VALUES (:biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, 
			:multi_dev_key, :dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :chg_date,
			:uri, :uri_type, :dev_id, :push_key, :user_type, :position);
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile INSERT FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}


/* 사용자 정보 변경 */
int prov_proc_chg_user_ref(int nid, char *sessionid, cJSON *root, char *ref, char *sup)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount, cnt;
	int		dCount;
	int		user_state;
	int		mpbx_type;			/* 1:VoLTE, 2:mVoIP, 3:ETC */
	int		mpbx_msp;			/* 1:SKT, 2:KT, 3:LTU+, 4:ETC, 9:유선만사용 */
	int		multi_dev_key;		/* 1~5 */
	int		dev_type;			/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os;				/* 1:Android, 2:IOS, 3:Windows, 4:ETC */
	int		uri_type=3;			/* 1:TEL-URI, 2:SIP-URI, 3:MSISDN, 4:ETC */

	char	user_id[24+1];
	char	biz_place_code[5+1];
	char	short_num[7+1];
	char	m_num[24+1];
	char	ext_num[24+1];
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	//char	user_name[50*2+1];
	//char	user_email[50+1];
	char	user_name[150+1];
	char	user_email[300+1];
	char	position[60+1];
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	char	user_pwd[64+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char	sub_svc[20+1];
	char    ins_date[14+1];
	char    chg_date[14+1];
	int		cf_type;
	char	cf_telno[24+1];
	char	uri[64+1];
	int		user_type;
	char	devTypeList[10+1];
	char	db_user_id[24+1];
	/* START, Ver 1.0.8, 2018.02.01 */
	int		short_num_len;
	/* END, Ver 1.0.8, 2018.02.01 */
	/* START, Ver 1.1.4, 2022.06.15 */
	char	push_type[255+1];
	char	hd_stat[32+1];
	char	sh[8+1];
	char	eh[8+1];
	char	st[4+1];
	char	et[4+1];
	int		wd;
	char	rdate[8+1];
	EXEC SQL END DECLARE SECTION;

	int		svc1=1,svc2=0,svc3=1,svc4=0,svc5=0,svc6=0, svc7=0;
	char	reason_text[50+1]="";
	char	*ptr;
	long	value;
	int		i, arr_cnt=0, idx;
	/* END, Ver 1.1.4, 2022.06.15 */
	int		len_user_name=0;
	char	charDevType;

	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset(user_id,			0x00, sizeof(user_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));
	memset(short_num,		0x00, sizeof(short_num));
	memset(m_num,			0x00, sizeof(m_num));
	memset(ext_num,			0x00, sizeof(ext_num));
	memset(user_name,		0x00, sizeof(user_name));
	memset(user_email,		0x00, sizeof(user_email));
	/* START, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(position,		0x00, sizeof(position));
	/* END, Ver 1.1.3, 2019.09.03 add POSITION */
	memset(user_pwd,		0x00, sizeof(user_pwd));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key,		0x00, sizeof (push_key));
	memset(sub_svc,			0x00, sizeof(sub_svc));
	memset(ins_date,		0x00, sizeof(ins_date));
	memset(chg_date,		0x00, sizeof(chg_date));
	memset(cf_telno,		0x00, sizeof(cf_telno));
	memset(uri,				0x00, sizeof(uri));
	memset (devTypeList, 	0x00, sizeof (devTypeList));
	memset(db_user_id,		0x00, sizeof(db_user_id));
	/* START, Ver 1.1.4, 2022.06.15 */
	memset(push_type,		0x00, sizeof(push_type));
	memset(hd_stat,		0x00, sizeof(hd_stat));	
	memset(sh,		0x00, sizeof(sh));
	memset(eh,		0x00, sizeof(eh));
	memset(st,		0x00, sizeof(st));
	memset(et,		0x00, sizeof(et));
	memset(rdate,		0x00, sizeof(rdate));
	/* END, Ver 1.1.4, 2022.06.15 */

	cf_type					= 0;
	user_type				= 0;
	

	/* START, Ver 1.1.4, add hd_stat json */
	/* HD_STAT */
	item = cJSON_GetObjectItem(root, "HD_STAT");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(hd_stat), "HD_STAT", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(hd_stat, item->valuestring);
		_mrs_logprint(DEBUG_5, "HD_STAT -------------------->[%s]\n", hd_stat);
		item=NULL;
	}
	/* END, Ver 1.1.4, add hd_stat json */

	/* START, ver1.1.4, 2022.06.15 add hd_stat = init */
	if(!strncmp(hd_stat, "INIT", 4))
	{

		/* START, Ver 1.1.4, 2022.06.15, set up */
		multi_dev_key = 0;	
		dev_type = 0;			
		dev_os = 0;				
		/* END, Ver 1.1.4, 2022.06.15, set up */
		/* USER_KEYID */
		item = cJSON_GetObjectItem(root, "USER_KEYID");
		if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(user_id, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
		item=NULL;

		/* BIZ_PLACE_CODE */
		item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
		if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
		_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);

		if(!strncmp(item->valuestring,"null",4))  return ERR_INVALID_DATA;

		_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);


		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_CODE", item->valuestring); return ERR_INVALID_DATA;}
		if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
		{
			_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
			return ERR_INVALID_DATA;
		}
		strcpy(biz_place_code, item->valuestring);
		_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
		item=NULL;

		/* 사업장이 등록되어 있는지 확인 */
		EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
					nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		if(nCount == 0){
			/* ERROR 리턴 */
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] BIZ_PLACE_CODE[%s] is empty in MPX_biz_place_info. - [%s][%d] %s\n",
					nid, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_NOT_REGISTERED;
		}


		/* mPBX_CUST_ID가 존재하는지 확인. */
		EXEC SQL AT :sessionid SELECT COUNT(*) INTO :nCount FROM MPX_USERPROFILE WHERE USER_ID=:user_id AND BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select db(MPX_USERPROFILE) Fail. - [%s][%d] %s\n",
					nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		if(nCount > 0){
			/* ERROR 리턴 */
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
					nid, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",user_id);
			sprintf(sup,"%s","");

			return ERR_USER_KEY_ALREADY_REG;
			//return ERR_ALREADY_REGISTERD;
		}

		/* USER_STATE */
		user_state = 1;			/* SET DEFAULT */
		item = cJSON_GetObjectItem(root, "USER_STATE");
		if(item){
			if(item->type == cJSON_Number) user_state = item->valueint; else user_state = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_STATE ------------------>[%d]\n", user_state);
			item=NULL;
		}

		/* mPBX_SHORT_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_SHORT_NUM");
		if(!item){ print_mis_para(__func__, "mPBX_SHORT_NUM"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_SHORT_NUM", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(short_num), "mPBX_SHORT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(short_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_SHORT_NUM -------------->[%s]\n", short_num);
		item=NULL;

		/* START, Ver 1.0.8, 2018.02.01 */
		/* 내선번호 길이가 맞는지 확인 */
		EXEC SQL AT :sessionid SELECT SHORTNUM_LEN INTO :short_num_len FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
					nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		if(strlen(short_num) != short_num_len){
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] input_short_num_len[%d] db_short_num_len[%d] diff Fail. [%s]\n",
				nid, strlen(short_num), short_num_len, sessionid);
			return ERR_INVALID_DATA;
		}
		/* END, Ver 1.0.8, 2018.02.01 */

		/* mPBX_M_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_M_NUM");
		if(!item){ print_mis_para(__func__, "mPBX_M_NUM"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_M_NUM", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(m_num), "mPBX_M_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(m_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_M_NUM ------------------>[%s]\n", m_num);
		item=NULL;

		/* Mobile번호에 대한 URI 체크 로직 */
		uri_type = _check_num_uri(m_num, uri);

		/* mPBX_MSP */
		item = cJSON_GetObjectItem(root, "mPBX_MSP");
		if(!item){ print_mis_para(__func__, "mPBX_MSP"); goto no_required;}
		if(item->type == cJSON_Number) mpbx_msp = item->valueint; else mpbx_msp = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_MSP -------------------->[%d]\n", mpbx_msp);
		/* START, Ver 1.1.2, 2018.09.10 */
		//if(mpbx_msp < 1 || mpbx_msp > 4)
		if(mpbx_msp < 1 || mpbx_msp > 5)
		/* END, Ver 1.1.2, 2018.09.10 */
		{
			_mrs_logprint(DEBUG_5, "Invalid mPBX_MSP Value -------------------->[%d]\n", mpbx_msp);
			return ERR_INVALID_DATA;
		}
		item=NULL;

		/* mPBX_EXT_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_EXT_NUM");
		if(!item){ print_mis_para(__func__, "mPBX_EXT_NUM"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_EXT_NUM", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(ext_num), "mPBX_EXT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(ext_num, item->valuestring);
		_mrs_logprint(DEBUG_5, "mPBX_EXT_NUM ---------------->[%s]\n", ext_num);
		item=NULL;

		/* USER_NAME */
		item = cJSON_GetObjectItem(root, "USER_NAME");
		if(!item){ print_mis_para(__func__, "USER_NAME"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_NAME", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(user_name), "USER_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
		_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), user_name, &len_user_name);
		//strcpy(user_name, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_NAME ------------------->[%s]\n", user_name);
		item=NULL;

		/* USER_EMAIL */
		item = cJSON_GetObjectItem(root, "USER_EMAIL");
		if(!item){ print_mis_para(__func__, "USER_EMAIL"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_EMAIL", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(user_email), "USER_EMAIL", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(user_email, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_EMAIL ------------------>[%s]\n", user_email);
		item=NULL;

		/* START, Ver 1.1.3, 2019.09.03 add POSITION */
		/* POSITION */
		item = cJSON_GetObjectItem(root, "POSITION");
		if(!item){ print_mis_para(__func__, "POSITION"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "POSITION", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(position), "POSITION", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(position, item->valuestring);
		_mrs_logprint(DEBUG_5, "POSITION ------------------>[%s]\n", position);
		item=NULL;
		/* END, Ver 1.1.3, 2019.09.03 add POSITION */

		/* USER_PWD */
		#ifdef _NOT_USE_V0_98
		item = cJSON_GetObjectItem(root, "USER_PWD");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(user_pwd), "USER_PWD", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(user_pwd, item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_PWD=[%s]\n", user_pwd);
			item=NULL;
		}
		#endif //_NOT_USE_V0_98

		/* USER_mPBX_TYPE */
		item = cJSON_GetObjectItem(root, "USER_mPBX_TYPE");
		if(!item){ print_mis_para(__func__, "USER_mPBX_TYPE"); goto no_required;}
		if(item->type == cJSON_Number) mpbx_type = item->valueint; 
		else 
		{
			value = strtol (item->valuestring, &ptr, 10);
			// 2017.01.11 "null" string으로 들어오는것에 대한 예외처리
			if (item->valuestring == ptr)
			{
				_mrs_logprint(DEBUG_2, "ERROR: USER_mPBX_TYPE VALUE[%S] isn't an integer \n", item->valuestring);
				return ERR_INVALID_DATA;
			}
			else
				mpbx_type = (int )value;
		}
		_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
		if(mpbx_type < 1 || mpbx_type > 3)
		{
			_mrs_logprint(DEBUG_5, "Invalid USER_mPBX_TYPE Value -------------------->[%d]\n", mpbx_type);
			return ERR_INVALID_DATA;
		}
		item=NULL;

		/* MULTI_DEV_KEY */
		item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
		if(item){
			if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
			/* 2016.08.19 multi_dev_key must be always 1 */
			/* 20170110 multi_dev_key가 다른것도 올수 있음.
			if (multi_dev_key != 1)
			{
				_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
				return PROV_SUCCESS;
			}
			*/
			/* ---------------- */
			item=NULL;
		}
		else
		{
			/* 2016.09.21 우리는 1만 처리하니가.. multi_dev_key가 없으면 1로 처리 */
			multi_dev_key = 1;
		}


		/* DEV_TYPE */
		item = cJSON_GetObjectItem(root, "DEV_TYPE");
		if(item){
			if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
			item=NULL;
		}

		/* DEV_OS */
		item = cJSON_GetObjectItem(root, "DEV_OS");
		if(item){
			if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
			item=NULL;
		}

		/* DEV_OS_VER */
		item = cJSON_GetObjectItem(root, "DEV_OS_VER");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_os_ver), "DEV_OS_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_os_ver, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
			item=NULL;
		}

		/* DEV_APP_VER */
		item = cJSON_GetObjectItem(root, "DEV_APP_VER");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_app_ver), "DEV_APP_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_app_ver, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
			item=NULL;
		}

		/* DEV_MODEL */
		item = cJSON_GetObjectItem(root, "DEV_MODEL");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_model), "DEV_MODEL", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_model, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
			item=NULL;
		}

		/* DEV_ID */
		item = cJSON_GetObjectItem(root, "DEV_ID");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_id), "DEV_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_id, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
			item=NULL;
		}

		/* PUSH_KEY */
		item = cJSON_GetObjectItem(root, "PUSH_KEY");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(push_key), "PUSH_KEY", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(push_key, item->valuestring);
			_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
			item=NULL;
		}

		/* START, Ver 1.1.4, add push_type json */
		/* PUSH_TYPE */
		item = cJSON_GetObjectItem(root, "PUSH_TYPE");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(push_type), "PUSH_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(push_type, item->valuestring);
			_mrs_logprint(DEBUG_5, "PUSH_TYPE -------------------->[%s]\n", push_type);
			item=NULL;
		}
		/* END, Ver 1.1.4, add push_type json */

		/* Set Default */
		_make_subsvc_str(sub_svc);

		/* SET USER_SUBSVC */
		/* MSP가 KT(2)인경우, 부가서비스 5,6번째 필드 설정 */
		if(mpbx_msp == MPX_MSP_KT){
			_set_subsvc_str(sub_svc, 5, 1);
			_set_subsvc_str(sub_svc, 6, 1);
		}

		/* START, Ver 1.1.2, 2018.09.10 */
		char cfg_status[1+1]="";
		int set_status =0;
		_get_cfg_subsvc_status(cfg_status);
		set_status = atoi(cfg_status);
		_mrs_logprint(DEBUG_5, "CFG SUBSVC STATUS -------------------->[%s][%d]\n", cfg_status,set_status);
		_set_subsvc_str(sub_svc, 9, set_status);
		/* END, Ver 1.1.2, 2018.09.10 */

		_mrs_sys_datestring_sec(ins_date);

		/* 20170405, USER_NUM, EMAIL 은 위에서 미리 CHECK */
		/* USER_NUM */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select ext_num&short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0)
		{
			/* USER_KEY */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] already registered. - [%s][%d] %s\n",
						nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",user_id);
				sprintf(sup,"%s","");

				return ERR_USER_KEY_ALREADY_REG;
			}

			/* M_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_M_NUM:%s",m_num);

				return ERR_M_NUM_ALREADY_REG;
			}


			// ERROR 리턴 
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] EXT_NUM[%s] SHORT_NUM[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, ext_num, short_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"USER_NUM:%s,%s",ext_num,short_num);

			return ERR_USER_NUM_ALREADY_REG;
		}


		/* EMAIL */
		EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_EMAIL = :user_email;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select email db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		if(dCount > 0)
		{
			/* USER_KEY */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] already registered. - [%s][%d] %s\n",
						nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",user_id);
				sprintf(sup,"%s","");

				return ERR_USER_KEY_ALREADY_REG;
			}

			/* M_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_M_NUM:%s",m_num);

				return ERR_M_NUM_ALREADY_REG;
			}

			/* SHORT_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref_chk] Select short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref_chk] USER_ID[%s] SHORT_NUM[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, short_num, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_SHORT_NUM:%s",short_num);

				return ERR_SHORT_NUM_ALREADY_REG;
			}


			// ERROR 리턴
			EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where USER_EMAIL = :user_email;
			_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] USER_ID[%s] EMAIL[%s] already registered. - [%s][%d] %s\n",
					nid, db_user_id, user_email, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

			sprintf(ref,"USER_KEYID:%s",db_user_id);
			sprintf(sup,"USER_EMAIL:%s",user_email);

			return ERR_EMAIL_ALREADY_REG;
		}


		/* MPX_USERPROFILE Table에 INSERT */
		/* START, Ver 1.1.4, 2022.06.15 add PUSH_TYPE, HD_STAT*/
		/* START, Ver 1.1.3, 2019.09.03 add POSITION */
		EXEC SQL AT :sessionid
				INSERT INTO MPX_USERPROFILE
							( BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME,
							USER_EMAIL, POSITION, USER_PWD, USER_mPBX_TYPE, MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER,
							DEV_APP_VER, DEV_MODEL, USER_SUBSVC, URI, URI_TYPE, INSERTDATE, UPDATEDATE, PUSH_TYPE, HD_STAT)
				VALUES		( :biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name,
							:user_email, :position, :user_pwd, :mpbx_type, :multi_dev_key, :dev_type, :dev_os, :dev_os_ver,
							:dev_app_ver, :dev_model, :sub_svc, :uri, :uri_type, :ins_date, :ins_date, :push_type, :hd_stat);
		/* END, Ver 1.1.3, 2019.09.03 add POSITION */
		/* START, Ver 1.1.4, 2022.06.15 add PUSH_TYPE, HD_STAT*/
		if(SQLCODE == -69720)
		{
			_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Already exist. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			
			/* SEARCH DUPLICATED DATA */
			/* USER_KEY, SHORT_NUM, M_NUM, USER_NUM, EMAIL */

			/* USER_KEY */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_ID = :user_id;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select user_key db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] already registered. - [%s][%d] %s\n",
						nid, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",user_id);
				sprintf(sup,"%s","");

				return ERR_USER_KEY_ALREADY_REG;
			}

			/* M_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where MOBILE_NUM = :m_num;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select m_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where MOBILE_NUM = :m_num;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] MOBILE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, m_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_M_NUM:%s",m_num);

				return ERR_M_NUM_ALREADY_REG;
			}

			/* SHORT_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
			if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
			{
				_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user_ref] Select short_num db(MPX_userprofile) Fail. - [%s][%d] %s\n", nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}
			if(dCount > 0){
				/* ERROR 리턴 */
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] SHORT_NUM[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, short_num, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_SHORT_NUM:%s",short_num);

				return ERR_SHORT_NUM_ALREADY_REG;
			}

			/* USER_NUM */
			/* 위에서 CHECK : 20170404
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
			if(dCount > 0){
				// ERROR 리턴 
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where EXT_NUM = :ext_num AND SHORT_NUM = :short_num;
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] EXT_NUM[%s] SHORT_NUM[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, ext_num, short_num, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"USER_NUM:%s",ext_num);

				return ERR_USER_NUM_ALREADY_REG;
			}
			*/

			/* EMAIL */
			/* 위에서 CHECK : 20170404
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where USER_EMAIL = :user_email;
			if(dCount > 0){
				// ERROR 리턴
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user] USER_ID[%s] EMAIL[%s] already registered. - [%s][%d] %s\n",
						nid, user_id, user_email, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",user_id);
				sprintf(sup,"USER_EMAIL:%s",user_email);

				return ERR_EMAIL_ALREADY_REG;
			}
			*/

			return ERR_ALREADY_REGISTERD;

		}
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Fail. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}
		_mrs_logprint(DEBUG_5, "(%s) MPX_USERPROFILE Insert Success. USER_ID[%s], PLACE_CODE[%s] - [%s]\n",__func__, user_id, biz_place_code, sessionid);

		/* START, Ver 1.1.4, add insert_user_holiday, insert_user_worktime */

		_mrs_sys_datestring_sec(rdate);

		/* 사용자별 휴일 관리 */
		/* USER_HOLIDAY */
		cJSON *holiday = cJSON_GetObjectItem(root, "USER_HOLIDAY");
		if(holiday){
			arr_cnt = cJSON_GetArraySize(holiday);
			for(i=0; i < arr_cnt; i++)
			{
				arritem = cJSON_GetArrayItem(holiday, i);
				if(!arritem){ print_mis_para(__func__, "USER_HOLIDAY"); goto no_required;}

				/* SH */
				subitem = cJSON_GetObjectItem(arritem, "SH");
				if(!subitem){ print_mis_para(__func__, "USER_HOLIDAY - SH"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(sh), "SH", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(sh, subitem->valuestring);
				subitem=NULL;
				
				/* EH */
				subitem = cJSON_GetObjectItem(arritem, "EH");
				if(!subitem){ print_mis_para(__func__, "USER_HOLIDAY - EH"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(eh), "EH", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(eh, subitem->valuestring);
				subitem=NULL;
				
				_mrs_logprint(DEBUG_5, " INDEX[%d] SH=[%s], EH=[%s] :  USER_ID=[%s]\n", i, sh, eh, user_id);

				/* INSERT 사용자별 휴일 관리 */
				EXEC SQL AT :sessionid INSERT INTO MPX_USER_HOLIDAY (BIZ_PLACE_CODE, SH, EH, USER_ID, RDATE) VALUES(:biz_place_code, :sh, :eh, :user_id, :rdate);
				if(sqlca.sqlcode != SQL_SUCCESS)
				{
					_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_HOLIDAY INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
							user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
					/* 실패처리 */
					return ERR_DB_HANDLING;
				}
			} // End of For
		}

		/* 사용자별 근무 시간 관리 */
		/* MPX_USER_WORKTIME */
		cJSON *worktime = cJSON_GetObjectItem(root, "USER_WORKTIME");
		if(worktime){
			/* Structure에 넣어서 중복 체크해야함 */
			if(_check_user_worktime_duplicate(worktime) != PROV_SUCCESS){ return ERR_INVALID_DATA; }

			arr_cnt = cJSON_GetArraySize(worktime);
			for(i=0; i < arr_cnt; i++)
			{
				arritem = cJSON_GetArrayItem(worktime, i);
				if(!arritem){ print_mis_para(__func__, "USER_WORKTIME"); goto no_required;}

				/* WD */
				subitem = cJSON_GetObjectItem(arritem, "WD");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - WD"); goto no_required;}
				if(subitem->type == cJSON_Number) wd = subitem->valueint; else wd = atoi(subitem->valuestring);
				subitem=NULL;

				/* ST */
				subitem = cJSON_GetObjectItem(arritem, "ST");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - ST"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(st), "ST", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(st, subitem->valuestring);
				subitem=NULL;

				/* ET */
				subitem = cJSON_GetObjectItem(arritem, "ET");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - ET"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(et), "ET", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(et, subitem->valuestring);
				subitem=NULL;
				
				_mrs_logprint(DEBUG_5, " INDEX[%d] WD=[%d] : ST[%s], ET[%s]\n", i, wd, st, et);

				/* INSERT 사용자별 근무시간 관리 */
				EXEC SQL AT :sessionid INSERT INTO MPX_USER_WORKTIME (BIZ_PLACE_CODE, WD, ST, ET, USER_ID, RDATE) VALUES(:biz_place_code, :wd, :st, :et, :user_id, :rdate);
				if(sqlca.sqlcode != SQL_SUCCESS)
				{
					_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_WORKTIME INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
							user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
					/* 실패처리 */
					return ERR_DB_HANDLING;
				}
			} // End of For
		}
		/* END, Ver 1.1.4, add insert_user_holiday, insert_user_worktime */
		return PROV_SUCCESS;	
	}
	/* END, ver1.1.4, 2022.06.15 add hd_stat = init */
	else{

		/* USER_KEYID */
		item = cJSON_GetObjectItem(root, "USER_KEYID");
		if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
		if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
		if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
		strcpy(user_id, item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
		item=NULL;


		/* UPDATE를 위해 기존 데이터를 가져온다. */
		/* START, ver1.1.4, 2022.06.15 add hd_stat, push_type */
		/* START, Ver 1.1.3, 2019.09.03 add POSITION */
		/*
		EXEC SQL AT :sessionid SELECT SHORT_NUM, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_mPBX_TYPE, 
			MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, URI_TYPE, URI, DEV_ID, PUSH_KEY, BIZ_PLACE_CODE,
			USER_TYPE, DEV_TYPE_LIST
		INTO :short_num, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, :multi_dev_key, 
			:dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :uri_type, :uri, :dev_id, :push_key, :biz_place_code,
			:user_type, :devTypeList
		FROM MPX_USERPROFILE 
		WHERE USER_ID=:user_id;
		*/
		EXEC SQL AT :sessionid SELECT SHORT_NUM, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_mPBX_TYPE, 
			MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, URI_TYPE, URI, DEV_ID, PUSH_KEY, BIZ_PLACE_CODE,
			USER_TYPE, DEV_TYPE_LIST, POSITION, PUSH_TYPE, HD_STAT
		INTO :short_num, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, :multi_dev_key, 
			:dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :uri_type, :uri, :dev_id, :push_key, :biz_place_code,
			:user_type, :devTypeList, :position, :push_type, :hd_stat
		FROM MPX_USERPROFILE 
		WHERE USER_ID=:user_id;
		/* END, Ver 1.1.3, 2019.09.03 add POSITION */
		/* END, ver1.1.4, 2022.06.15 add hd_stat, push_type */

		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo SELECT FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_NOT_REGISTERED;
		}

		_mrs_logprint(DEBUG_5, "DEV_TYPE_LIST ----------------->[%s]\n", devTypeList);

		/* BIZ_PLACE_CODE */
		item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");
		if (item)
		{
			if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
			{
				_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
				return ERR_MISSING_PARAMETER;
			}
			strcpy(biz_place_code, item->valuestring);
			_mrs_logprint(DEBUG_5, "BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);

			/* biz_place_code 변경.... 해당 사업장 존재하는지 확인 */
			EXEC SQL AT :sessionid
				SELECT COUNT(*) INTO :cnt FROM MPX_BIZ_PLACE_INFO 
				WHERE BIZ_PLACE_CODE = :biz_place_code;
			if(sqlca.sqlcode != SQL_SUCCESS)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_BIZ_PLACE_INFO SELECT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n", biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				return ERR_DB_HANDLING;
			}

			/* biz not exist */
			if (cnt == 0)
			{
				_mrs_logprint_thr (DEBUG_1, "BIZ NOT EXIST. BIZ_PLACE_CODE[%s]\n", biz_place_code);
				return ERR_NOT_REGISTERED;
			}
		}
		item=NULL;

		/* USER_STATE */
		user_state = 1;			/* SET DEFAULT */
		item = cJSON_GetObjectItem(root, "USER_STATE");
		if(item){
			if(item->type == cJSON_Number) user_state = item->valueint; else user_state = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_STATE ------------------>[%d]\n", user_state);
			item=NULL;
		}

		/* mPBX_SHORT_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_SHORT_NUM");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_SHORT_NUM", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(short_num), "mPBX_SHORT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(short_num, item->valuestring);
			_mrs_logprint(DEBUG_5, "mPBX_SHORT_NUM -------------->[%s]\n", short_num);
			item=NULL;
		}

		/* START, Ver 1.0.8, 2018.02.01 */
		/* 내선번호 길이가 맞는지 확인 */
		EXEC SQL AT :sessionid SELECT SHORTNUM_LEN INTO :short_num_len FROM MPX_BIZ_PLACE_INFO WHERE BIZ_PLACE_CODE=:biz_place_code;
		if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
		{
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] Select db(MPX_biz_place_info) Fail. - [%s][%d] %s\n",
					nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		if(strlen(short_num) != short_num_len){
			_mrs_logprint(DEBUG_2, "????? nid[%d] [prov_proc_add_user] input_short_num_len[%d] db_short_num_len[%d] diff Fail. [%s]\n",
				nid, strlen(short_num), short_num_len, sessionid);
			return ERR_INVALID_DATA;
		}
		/* END, Ver 1.0.8, 2018.02.01 */

		/* mPBX_M_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_M_NUM");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_M_NUM", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(m_num), "mPBX_M_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(m_num, item->valuestring);
			_mrs_logprint(DEBUG_5, "mPBX_M_NUM ------------------>[%s]\n", m_num);
			item=NULL;

			/* Mobile번호에 대한 URI 체크 로직 */
			uri_type = _check_num_uri(m_num, uri);
		}

		/* mPBX_MSP */
		item = cJSON_GetObjectItem(root, "mPBX_MSP");
		if(item){
			if(item->type == cJSON_Number) mpbx_msp = item->valueint; else mpbx_msp = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "mPBX_MSP -------------------->[%d]\n", mpbx_msp);
			/* START, Ver 1.1.2, 2018.09.10 */
			//if(mpbx_msp < 1 || mpbx_msp > 4) 
			if(mpbx_msp < 1 || mpbx_msp > 5) 
			/* END, Ver 1.1.2, 2018.09.10 */
			{
				_mrs_logprint(DEBUG_5, "Invalid mPBX_MSP Value -------------------->[%d]\n", mpbx_msp);
				return ERR_INVALID_DATA;
			}
			item=NULL;
		}

		/* mPBX_EXT_NUM */
		item = cJSON_GetObjectItem(root, "mPBX_EXT_NUM");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "mPBX_EXT_NUM", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(ext_num), "mPBX_EXT_NUM", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(ext_num, item->valuestring);
			_mrs_logprint(DEBUG_5, "mPBX_EXT_NUM ---------------->[%s]\n", ext_num);
			item=NULL;
		}

		/* USER_NAME */
		item = cJSON_GetObjectItem(root, "USER_NAME");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_NAME", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(user_name), "USER_NAME", item->valuestring)<0){return ERR_INVALID_DATA;}
			_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), user_name, &len_user_name);
			//strcpy(user_name, item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_NAME ------------------->[%s]\n", user_name);
			item=NULL;
		}

		/* USER_EMAIL */
		item = cJSON_GetObjectItem(root, "USER_EMAIL");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_EMAIL", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(user_email), "USER_EMAIL", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(user_email, item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_EMAIL ------------------>[%s]\n", user_email);
			item=NULL;
		}

		/* START, Ver 1.1.3, 2019.09.03 add POSITION */
		/* POSITION */
		item = cJSON_GetObjectItem(root, "POSITION");
		if(item){
			if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "POSITION", item->valuestring); return ERR_INVALID_DATA;}
			if(compare_chr_len(strlen(item->valuestring), sizeof(position), "POSITION", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(position, item->valuestring);
			_mrs_logprint(DEBUG_5, "POSITION ------------------>[%s]\n", position);
			item=NULL;
		}
		/* END, Ver 1.1.3, 2019.09.03 add POSITION */

	#ifdef _NOT_USE_V0_99
		/* USER_PWD */
		item = cJSON_GetObjectItem(root, "USER_PWD");
		if(item){
			strcpy(user_pwd, item->valuestring);
			if(compare_chr_len(strlen(item->valuestring), sizeof(user_pwd), "USER_PWD", item->valuestring)<0){return ERR_INVALID_DATA;}
			_mrs_logprint(DEBUG_5, "USER_PWD=[%s]\n", user_pwd);
			item=NULL;
		}
	#endif //_NOT_USE_V0_99

		/* DEV_ID */
		item = cJSON_GetObjectItem(root, "DEV_ID");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_id), "DEV_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_id, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
			item=NULL;
		}

		/* PUSH_KEY */
		item = cJSON_GetObjectItem(root, "PUSH_KEY");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(push_key), "PUSH_KEY", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(push_key, item->valuestring);
			_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
			item=NULL;
		}

		/* USER_mPBX_TYPE */
		item = cJSON_GetObjectItem(root, "USER_mPBX_TYPE");
		if(item){
			if(item->type == cJSON_Number) mpbx_type = item->valueint; else mpbx_type = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
			item=NULL;
		}

		/* MULTI_DEV_KEY */
		item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
		if(item){
			if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
			/* 2016.08.19 multi_dev_key must be always 1 */
			/* 20170110 multi_dev_key가 다른것도 올수 있음. v1.0.5 add
			if (multi_dev_key != 1)
			{
				_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
				return PROV_SUCCESS;
			}
			*/
			/* 2016.08.19 multi_dev_key must be always 1 */
			if (multi_dev_key != 1)
			{
				charDevType = multi_dev_key + '0';		//integer to character
				chk_dev_type (devTypeList, strlen (devTypeList), charDevType, 1);
				if (multi_dev_key != 1)
				{
					_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
					return PROV_SUCCESS;
				}
			}
			/* ---------------- */
			item=NULL;
		}

		/* DEV_TYPE */
		item = cJSON_GetObjectItem(root, "DEV_TYPE");
		if(item){
			if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
			item=NULL;
		}

		/* DEV_OS */
		item = cJSON_GetObjectItem(root, "DEV_OS");
		if(item){
			if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
			item=NULL;
		}

		/* DEV_OS_VER */
		item = cJSON_GetObjectItem(root, "DEV_OS_VER");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_os_ver), "DEV_OS_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_os_ver, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
			item=NULL;
		}

		/* DEV_APP_VER */
		item = cJSON_GetObjectItem(root, "DEV_APP_VER");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_app_ver), "DEV_APP_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_app_ver, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
			item=NULL;
		}

		/* DEV_MODEL */
		item = cJSON_GetObjectItem(root, "DEV_MODEL");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(dev_model), "DEV_MODEL", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(dev_model, item->valuestring);
			_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
			item=NULL;
		}

		/* START, Ver 1.1.4, add push_type json */
		/* PUSH_TYPE */
		item = cJSON_GetObjectItem(root, "PUSH_TYPE");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(push_type), "PUSH_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(push_type, item->valuestring);
			_mrs_logprint(DEBUG_5, "PUSH_TYPE -------------------->[%s]\n", push_type);
			item=NULL;
		}

		/* HD_STAT */
		item = cJSON_GetObjectItem(root, "HD_STAT");
		if(item){
			if(compare_chr_len(strlen(item->valuestring), sizeof(hd_stat), "HD_STAT", item->valuestring)<0){return ERR_INVALID_DATA;}
			strcpy(hd_stat, item->valuestring);
			_mrs_logprint(DEBUG_5, "HD_STAT -------------------->[%s]\n", hd_stat);
			item=NULL;
		}
		/* END, Ver 1.1.4, add push_type json */

		/* 08.19 ,   ygkim
		if ( (dev_type != 0 || dev_os != 0 || strlen (dev_os_ver) > 0 
			|| strlen (dev_app_ver) > 0 || strlen (dev_model) > 0) && multi_dev_key == 0)
		{
			_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY is missed for updating Device info.\n");
			goto no_required;
		}
		*/

		_mrs_sys_datestring_sec(chg_date);

		/* Insert MPX_USERPROFILE Table after deleted table */
		/* Delete */
		EXEC SQL AT :sessionid DELETE FROM MPX_USERPROFILE WHERE USER_ID = :user_id;
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_CustomInfo DELETE FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		/* INSERT */
		/*
		_mrs_logprint (DEBUG_9, "--- INSERT VALUE\n");
		_mrs_logprint (DEBUG_9, "--- BIZ_PLACE_CODE: %100s\n", biz_place_code);
		_mrs_logprint (DEBUG_9, "--- SHORT_NUM: %100s\n", short_num);
		_mrs_logprint (DEBUG_9, "--- USER_ID: %100s\n", user_id);
		_mrs_logprint (DEBUG_9, "--- USER_STATUS: %100d\n", user_state);
		_mrs_logprint (DEBUG_9, "--- MOBILE_NUM: %100s\n", m_num);
		_mrs_logprint (DEBUG_9, "--- MSP: %100d\n", mpbx_msp);
		_mrs_logprint (DEBUG_9, "--- EXT_NUM: %100s\n", ext_num);
		_mrs_logprint (DEBUG_9, "--- USER_NAME: %100s\n", user_name);
		_mrs_logprint (DEBUG_9, "--- USER_EMAIL: %100s\n", user_email);
		_mrs_logprint (DEBUG_9, "--- USER_MPBX_TYPE: %100d\n", mpbx_type);
		_mrs_logprint (DEBUG_9, "--- MULTI_DEV_KEY: %100d\n", multi_dev_key);
		_mrs_logprint (DEBUG_9, "--- DEV_TYPE: %100d\n", dev_type);
		_mrs_logprint (DEBUG_9, "--- DEV_OS: %100d\n", dev_os);
		_mrs_logprint (DEBUG_9, "--- DEV_OS_VER: %100s\n", dev_os_ver);
		_mrs_logprint (DEBUG_9, "--- DEV_APP_VER: %100s\n", dev_app_ver);
		_mrs_logprint (DEBUG_9, "--- DEV_MODEL: %100s\n", dev_model);
		_mrs_logprint (DEBUG_9, "--- USER_SUBSVC: %100s\n", sub_svc);
		_mrs_logprint (DEBUG_9, "--- CF_TYPE: %100d\n", cf_type);
		_mrs_logprint (DEBUG_9, "--- CF_TELNO: %100s\n", cf_telno);
		_mrs_logprint (DEBUG_9, "--- INSERTDATE: %100s\n", ins_date);
		_mrs_logprint (DEBUG_9, "--- UPDATEDATE: %100s\n", chg_date);
		_mrs_logprint (DEBUG_9, "--- URI: %100s\n", uri);
		_mrs_logprint (DEBUG_9, "--- URI_TYPE: %100d\n", uri_type);
		_mrs_logprint (DEBUG_9, "--- DEV_ID: %100s\n", dev_id);
		_mrs_logprint (DEBUG_9, "--- PUSH_KEY: %100s\n", push_key);
		_mrs_logprint (DEBUG_9, "--- USER_TYPE: %100d\n", user_type);
		*/

		/* START, Ver 1.1.4, add push_type, hd_stat */
		/* START, Ver 1.1.3, 2019.09.03 add POSITION */
		/*
		EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE (BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_MPBX_TYPE, 
				MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, UPDATEDATE, 
				URI, URI_TYPE, DEV_ID, PUSH_KEY, USER_TYPE)
			VALUES (:biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, 
				:multi_dev_key, :dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :chg_date,
				:uri, :uri_type, :dev_id, :push_key, :user_type);
		*/
		EXEC SQL AT :sessionid
			INSERT INTO MPX_USERPROFILE (BIZ_PLACE_CODE, SHORT_NUM, USER_ID, USER_STATUS, MOBILE_NUM, MSP, EXT_NUM, USER_NAME, USER_EMAIL, USER_MPBX_TYPE, 
				MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, USER_SUBSVC, CF_TYPE, CF_TELNO, INSERTDATE, UPDATEDATE, 
				URI, URI_TYPE, DEV_ID, PUSH_KEY, USER_TYPE, POSITION, PUSH_TYPE, HD_STAT)
			VALUES (:biz_place_code, :short_num, :user_id, :user_state, :m_num, :mpbx_msp, :ext_num, :user_name, :user_email, :mpbx_type, 
				:multi_dev_key, :dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :sub_svc, :cf_type, :cf_telno, :ins_date, :chg_date,
				:uri, :uri_type, :dev_id, :push_key, :user_type, :position, :push_type, :hd_stat);
		/* END, Ver 1.1.3, 2019.09.03 add POSITION */
		/* END, Ver 1.1.4, add push_type, hd_stat */

		if(SQLCODE == -69720)
		{
			_mrs_logprint(DEBUG_2, "(%s) MPX_USERPROFILE Insert Already exist. USER_ID[%s], PLACE_CODE[%s] - [%s][%d] %s\n", __func__, user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			
			/* SEARCH DUPLICATED DATA */
			/* SHORT_NUM */
			EXEC SQL AT :sessionid SELECT count(*) INTO :dCount from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
			if(dCount > 0){
				EXEC SQL AT :sessionid SELECT USER_ID INTO :db_user_id from MPX_USERPROFILE where SHORT_NUM = :short_num AND BIZ_PLACE_CODE=:biz_place_code;
				/* ERROR 리턴 */
				_mrs_logprint(DEBUG_2, "nid[%d] [prov_proc_add_user_ref] USER_ID[%s] SHORT_NUM[%s] BIZ_PLACE_CODE[%s] already registered. - [%s][%d] %s\n",
						nid, db_user_id, short_num, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

				sprintf(ref,"USER_KEYID:%s",db_user_id);
				sprintf(sup,"mPBX_SHORT_NUM:%s",short_num);

				return ERR_SHORT_NUM_ALREADY_REG;
			}
		}

		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile INSERT FAIL. BIZ_PLACE_CODE[%s], USER_ID[%s] - [%s][%d] %s\n", biz_place_code, user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		/* DEV_TYPE_LIST update  2017.04.03 */
		EXEC SQL AT:sessionid
			UPDATE MPX_USERPROFILE SET DEV_TYPE_LIST = :devTypeList
			WHERE USER_ID=:user_id;
		if(sqlca.sqlcode != SQL_SUCCESS)
		{
			_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile Device list UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
			return ERR_DB_HANDLING;
		}

		/* START, Ver 1.1.4, add update_user_holiday, update_user_worktime */

		_mrs_sys_datestring_sec(rdate);

		/* 사용자별 휴일 관리 */
		/* MPX_USER_HOLIDAY */
		cJSON *holiday = cJSON_GetObjectItem(root, "USER_HOLIDAY");
		if(holiday){
			/* 2016.06.17 instert data after delete */
			EXEC SQL AT :sessionid 
					DELETE 
					FROM 	MPX_USER_HOLIDAY 
					WHERE 	BIZ_PLACE_CODE = :biz_place_code and USER_ID = :user_id;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA_FOUND)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_HOLIDAY DELTE FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
					user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}

			arr_cnt = cJSON_GetArraySize(holiday);
			for(i=0; i < arr_cnt; i++)
			{
				arritem = cJSON_GetArrayItem(holiday, i);
				if(!arritem){ print_mis_para(__func__, "USER_HOLIDAY"); goto no_required;}

				/* SH */
				subitem = cJSON_GetObjectItem(arritem, "SH");
				if(!subitem){ print_mis_para(__func__, "USER_HOLIDAY - SH"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(sh), "SH", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(sh, subitem->valuestring);
				subitem=NULL;
				
				/* EH */
				subitem = cJSON_GetObjectItem(arritem, "EH");
				if(!subitem){ print_mis_para(__func__, "USER_HOLIDAY - EH"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(eh), "EH", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(eh, subitem->valuestring);
				subitem=NULL;

				_mrs_logprint(DEBUG_5, " INDEX[%d] SH=[%s], EH=[%s] : USER_ID=[%s]\n", i, sh, eh, user_id);

				/* INSERT 사용자별 휴일 관리 */
				EXEC SQL AT :sessionid INSERT INTO MPX_USER_HOLIDAY (BIZ_PLACE_CODE, SH, EH, USER_ID, RDATE) VALUES(:biz_place_code, :sh, :eh, :user_id, :rdate);
				if(sqlca.sqlcode != SQL_SUCCESS)
				{
					_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_HOLIDAY INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
						user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
					/* 실패처리 */
					return ERR_DB_HANDLING;
				}
			} // End of For
		}

		/* 사용자별 근무 시간 관리 */
		/* MPX_USER_WORKTIME */
		cJSON *worktime = cJSON_GetObjectItem(root, "USER_WORKTIME");

		if(worktime){
			/* Structure에 넣어서 중복 체크해야함 */
			if(_check_user_worktime_duplicate(worktime) != PROV_SUCCESS){ return ERR_INVALID_DATA; }

			/* 2016.06.17 insert after delete */
			EXEC SQL AT :sessionid 
					DELETE 
					FROM 	MPX_USER_WORKTIME 
					WHERE 	BIZ_PLACE_CODE = :biz_place_code and USER_ID = :user_id;
			if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA_FOUND)
			{
				_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_WORKTIME DELTE FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
						user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
				/* 실패처리 */
				return ERR_DB_HANDLING;
			}

			arr_cnt = cJSON_GetArraySize(worktime);
			for(i=0; i < arr_cnt; i++)
			{
				arritem = cJSON_GetArrayItem(worktime, i);
				if(!arritem){ print_mis_para(__func__, "USER_WORKTIME"); goto no_required;}

				/* WD */
				subitem = cJSON_GetObjectItem(arritem, "WD");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - WD"); goto no_required;}
				if(subitem->type == cJSON_Number) wd = subitem->valueint; else wd = atoi(subitem->valuestring);
				subitem=NULL;

				/* ST */
				subitem = cJSON_GetObjectItem(arritem, "ST");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - ST"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(st), "ST", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(st, subitem->valuestring);
				subitem=NULL;

				/* ET */
				subitem = cJSON_GetObjectItem(arritem, "ET");
				if(!subitem){ print_mis_para(__func__, "USER_WORKTIME - ET"); goto no_required;}
				if(compare_chr_len(strlen(subitem->valuestring), sizeof(et), "ET", subitem->valuestring)<0){return ERR_INVALID_DATA;}
				if(strlen(subitem->valuestring) > 0)
					strcpy(et, subitem->valuestring);
				subitem=NULL;

				_mrs_logprint(DEBUG_5, " INDEX[%d] WD=[%d] : ST[%s], ET[%s]\n", i, wd, st, et);

				/* INSERT 사용자별 근무시간 관리 */
				EXEC SQL AT :sessionid 
						INSERT 
						INTO 	MPX_USER_WORKTIME 
								(BIZ_PLACE_CODE, WD, ST, ET, USER_ID, RDATE) 
						VALUES	(:biz_place_code, :wd, :st, :et, :user_id, :rdate);
				if(sqlca.sqlcode != SQL_SUCCESS)
				{
					_mrs_logprint(DEBUG_2, " MPBX PROV USER_ID[%s] - MPX_USER_WORKTIME INSERT FAIL. BIZ_PLACE_CODE[%s] - [%s][%d] %s\n",
							user_id, biz_place_code, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
					/* 실패처리 */
					return ERR_DB_HANDLING;
				}
			} // End of For
		}
		/* END, Ver 1.1.4, add update_user_holiday, update_user_worktime */

		return PROV_SUCCESS;
	}

no_required:
	return ERR_MISSING_PARAMETER;
}


int prov_proc_chg_dev(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	int		multi_dev_key;		/* 1~5 */
	int		dev_type;			/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os;				/* 1:Android, 2:IOS, 3:Windows, 4:ETC */

	char	user_id[24+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char    chg_date[14+1];
	char	devTypeList[10+1];
	/* START, Ver 1.1.4, 2022.06.15 */
	char	push_type[255+1];
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	char	charDevType;

	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset (user_id, 		0x00, sizeof (user_id));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key,		0x00, sizeof (push_key));
	memset(chg_date,		0x00, sizeof(chg_date));
	memset (devTypeList, 	0x00, sizeof (devTypeList));
	/* START, Ver 1.1.4, 2022.06.15 */
	memset(push_type,		0x00, sizeof(push_type));
	/* END, Ver 1.1.4, 2022.06.15 */

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	/* START, Ver 1.1.4, add push_type */
	/* UPDATE를 위해 기존 데이터를 가져온다. */
	EXEC SQL AT :sessionid 
			SELECT 	MULTI_DEV_KEY, DEV_TYPE, DEV_OS, DEV_OS_VER, DEV_APP_VER, DEV_MODEL, DEV_ID, PUSH_KEY, DEV_TYPE_LIST, PUSH_TYPE
			INTO 	:multi_dev_key, :dev_type, :dev_os, :dev_os_ver, :dev_app_ver, :dev_model, :dev_id, :push_key, :devTypeList, :push_type
			FROM 	MPX_USERPROFILE 
			WHERE 	USER_ID=:user_id LIMIT 1;
	/* END, Ver 1.1.4, add push_type */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile SELECT FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}
	_mrs_logprint(DEBUG_3, "(db-data) MULTI_DEV_KEY[%d], DEV_TYPE[%d], DEV_OS[%d], DEV_OS_VER[%s], DEV_APP_VER[%s]\n", 
												multi_dev_key, dev_type, dev_os, dev_os_ver, dev_app_ver);
	_mrs_logprint(DEBUG_3, "(db-data) DEV_MODEL[%s], DEV_ID[%s], PUSH_KEY[%s]\n", dev_model, dev_id, push_key);

	/* DEV_ID */
	item = cJSON_GetObjectItem(root, "DEV_ID");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_id), "DEV_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(dev_id, item->valuestring);
		else dev_id[0] = 0x00;
		_mrs_logprint(DEBUG_5, "DEV_ID ---------------------->[%s]\n", dev_id);
		item=NULL;
	}

	/* PUSH_KEY */
	item = cJSON_GetObjectItem(root, "PUSH_KEY");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(push_key), "PUSH_KEY", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(push_key, item->valuestring);
		else push_key[0]=0x00;
		_mrs_logprint(DEBUG_5, "PUSH_KEY -------------------->[%s]\n", push_key);
		item=NULL;
	}

	/* DEV_TYPE */
	item = cJSON_GetObjectItem(root, "DEV_TYPE");
	if(item){
		if(item->type == cJSON_Number) dev_type = item->valueint; else dev_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_TYPE -------------------->[%d]\n", dev_type);
		item=NULL;
	}

	/* MULTI_DEV_KEY */
	item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
	if(item){
		if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
		/* 2016.08.19 multi_dev_key must be always 1 */
		if (multi_dev_key != 1)
		{
			charDevType = multi_dev_key + '0';		//integer to character
			chk_dev_type (devTypeList, strlen (devTypeList), charDevType, 1);
			EXEC SQL AT:sessionid
				UPDATE MPX_USERPROFILE SET DEV_TYPE_LIST = :devTypeList
				WHERE USER_ID=:user_id;

			if(sqlca.sqlcode != SQL_SUCCESS)
    		{
    		    _mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile Device list UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
    		    return ERR_DB_HANDLING;
    		}

			if (multi_dev_key != 1)
			{
				_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
				return PROV_SUCCESS;
			}
		}
		/* ---------------- */
		item=NULL;
	}
	else
	{
		/* 2016.09.21 우리는 1만 처리하니가.. multi_dev_key가 없으면 1로 처리 */
		multi_dev_key = 1;
	}

	/* DEV_OS */
	item = cJSON_GetObjectItem(root, "DEV_OS");
	if(item){
		if(item->type == cJSON_Number) dev_os = item->valueint; else dev_os = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "DEV_OS ---------------------->[%d]\n", dev_os);
		item=NULL;
	}

	/* DEV_OS_VER */
	item = cJSON_GetObjectItem(root, "DEV_OS_VER");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_os_ver), "DEV_OS_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(dev_os_ver, item->valuestring);
		else dev_os_ver[0] = 0x00;
		_mrs_logprint(DEBUG_5, "DEV_OS_VER ------------------>[%s]\n", dev_os_ver);
		item=NULL;
	}

	/* DEV_APP_VER */
	item = cJSON_GetObjectItem(root, "DEV_APP_VER");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_app_ver), "DEV_APP_VER", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(dev_app_ver, item->valuestring);
		else dev_app_ver[0] = 0x00;
		_mrs_logprint(DEBUG_5, "DEV_APP_VER ----------------->[%s]\n", dev_app_ver);
		item=NULL;
	}

	/* DEV_MODEL */
	item = cJSON_GetObjectItem(root, "DEV_MODEL");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(dev_model), "DEV_MODEL", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(dev_model, item->valuestring);
		else dev_model[0] = 0x00;
		_mrs_logprint(DEBUG_5, "DEV_MODEL ------------------->[%s]\n", dev_model);
		item=NULL;
	}

	/* START, Ver 1.1.4, add push_type json */
	/* PUSH_TYPE */
	item = cJSON_GetObjectItem(root, "PUSH_TYPE");
	if(item){
		if(compare_chr_len(strlen(item->valuestring), sizeof(push_type), "PUSH_TYPE", item->valuestring)<0){return ERR_INVALID_DATA;}
		if(strlen(item->valuestring) > 0)
			strcpy(push_type, item->valuestring);
		else push_type[0] = 0x00;
		_mrs_logprint(DEBUG_5, "PUSH_TYPE ------------------->[%s]\n", push_type);
		item=NULL;
	}
	/* END, Ver 1.1.4, add push_type json */

	/* 2016.07.19 multi_dev_key must be always 1 */
	/*
	if (multi_dev_key != 1)
	{
		_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1\n");
		return ERR_INVALID_DATA;
	}
	else if ( (dev_type != 0 || dev_os != 0 || strlen (dev_os_ver) > 0
		|| strlen (dev_app_ver) > 0 || strlen (dev_model) > 0) && multi_dev_key == 0)
	{
		_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY is missed for updating Device info.\n");
		goto no_required;
	}
	*/

	_mrs_sys_datestring_sec(chg_date);

	/* START, Ver 1.1.4, add push_type */
	/* MPX_USERPROFILE Table에 UPDATE */
	/*
	EXEC SQL AT :sessionid UPDATE MPX_USERPROFILE SET USER_STATUS=:user_state, MOBILE_NUM=:m_num, MSP=:mpbx_msp, EXT_NUM=:ext_num, USER_NAME=:user_name, USER_EMAIL=:user_email, USER_PWD=:user_pwd, USER_mPBX_TYPE=:mpbx_type, MULTI_DEV_KEY=:multi_dev_key, DEV_TYPE=:dev_type, DEV_OS=:dev_os, DEV_OS_VER=:dev_os_ver, DEV_APP_VER=:dev_app_ver, DEV_MODEL=:dev_model, USER_SUBSVC=:sub_svc, URI=:uri, URI_TYPE=:uri_type, INSERTDATE=:ins_date, UPDATEDATE=:chg_date WHERE BIZ_PLACE_CODE=:biz_place_code AND USER_ID=:user_id;
	*/
	/* 2016.07.19 v0.99 */
	EXEC SQL AT :sessionid UPDATE MPX_USERPROFILE SET MULTI_DEV_KEY=:multi_dev_key, DEV_TYPE=:dev_type, DEV_OS=:dev_os, DEV_OS_VER=:dev_os_ver, DEV_APP_VER=:dev_app_ver, DEV_MODEL=:dev_model, UPDATEDATE=:chg_date, DEV_ID = :dev_id, PUSH_KEY = :push_key, PUSH_TYPE = :push_type WHERE USER_ID=:user_id;
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}
	/* END, Ver 1.1.4, add push_type */

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

int prov_proc_del_dev(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount;
	int		multi_dev_key;		/* 1~5 */
	int		dev_type;			/* 1:Mobile, 2:PC, 3:PAD, 4:IP-Phone, 5:ETC */
	int		dev_os;				/* 1:Android, 2:IOS, 3:Windows, 4:ETC */

	char	user_id[24+1];
	char	dev_os_ver[10+1];
	char	dev_app_ver[10+1];
	char	dev_model[20+1];
	char	dev_id[64+1];
	char	push_key[500+1];
	char    chg_date[14+1];
	char	devTypeList[10+1];
	/* START, Ver 1.1.4, 2022.06.15 */
	char	push_type[255+1];
	/* END, Ver 1.1.4, 2022.06.15 */
	EXEC SQL END DECLARE SECTION;

	char	charDevType;

	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset (user_id, 		0x00, sizeof (user_id));
	memset(dev_os_ver,		0x00, sizeof(dev_os_ver));
	memset(dev_app_ver,		0x00, sizeof(dev_app_ver));
	memset(dev_model,		0x00, sizeof(dev_model));
	memset (dev_id, 		0x00, sizeof (dev_id));
	memset (push_key,		0x00, sizeof (push_key));
	memset(chg_date,		0x00, sizeof(chg_date));
	memset (devTypeList, 	0x00, sizeof (devTypeList));
	/* START, Ver 1.1.4, 2022.06.15 */
	memset(push_type,		0x00, sizeof(push_type));
	/* END, Ver 1.1.4, 2022.06.15 */

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	/* UPDATE를 위해 기존 데이터를 가져온다. */
	EXEC SQL AT :sessionid 
			SELECT 	DEV_TYPE_LIST
			INTO 	:devTypeList
			FROM 	MPX_USERPROFILE 
			WHERE 	USER_ID=:user_id LIMIT 1;
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile SELECT FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}

	/* MULTI_DEV_KEY */
	item = cJSON_GetObjectItem(root, "MULTI_DEV_KEY");
	if(item){
		if(item->type == cJSON_Number) multi_dev_key = item->valueint; else multi_dev_key = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "MULTI_DEV_KEY --------------->[%d]\n", multi_dev_key);
		/* 2016.08.19 multi_dev_key must be always 1 */
		if (multi_dev_key != 1)
		{
			charDevType = multi_dev_key + '0';		//integer to character
			chk_dev_type (devTypeList, strlen (devTypeList), charDevType, 2);
			EXEC SQL AT:sessionid
				UPDATE MPX_USERPROFILE SET DEV_TYPE_LIST = :devTypeList
				WHERE USER_ID=:user_id;

			if(sqlca.sqlcode != SQL_SUCCESS)
    		{
    		    _mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile Device list UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
    		    return ERR_DB_HANDLING;
    		}

			if (multi_dev_key != 1)
			{
				_mrs_logprint (DEBUG_1, "MULTI_DEV_KEY isn't 1, Pass, return success\n");
				return PROV_SUCCESS;
			}
		}
		/* ---------------- */
		item=NULL;
	}
	else
	{
		/* 2016.09.21 우리는 1만 처리하니가.. multi_dev_key가 없으면 1로 처리 */
		multi_dev_key = 1;
	}
	_mrs_logprint(DEBUG_3, "(input-data) USER_ID[%d], MULTI_DEV_TYPE[%d]\n", user_id, multi_dev_key);


	/* come to must multi_dev_type's value is 1
		delete user device data */

	_mrs_sys_datestring_sec(chg_date);

	/* START, Ver 1.1.4, add push_type */
	EXEC SQL AT :sessionid UPDATE MPX_USERPROFILE SET 
		MULTI_DEV_KEY=null, DEV_TYPE=null, DEV_OS=null, DEV_OS_VER=null,
		DEV_APP_VER=null, DEV_MODEL=null, UPDATEDATE=:chg_date, DEV_ID = null, PUSH_KEY = null, PUSH_TYPE = null
		WHERE USER_ID=:user_id;
	/* END, Ver 1.1.4, add push_type */
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

int prov_proc_chg_svc(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	int		nCount, i = 0;
	int		mpbx_type;

	char	user_subsvc[20+1];
	char	user_id[24+1];
	char    chg_date[14+1];

	EXEC SQL END DECLARE SECTION;

	cJSON 	*item=NULL;
	cJSON 	*subitem=NULL;
	cJSON 	*arritem=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset (user_subsvc, 	0x00, sizeof (user_subsvc));
	memset (user_id, 		0x00, sizeof (user_id));
	memset (chg_date,		0x00, sizeof (chg_date));

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	/* UPDATE를 위해 기존 데이터를 가져온다. */
	EXEC SQL AT :sessionid
			SELECT 	USER_SUBSVC, USER_mPBX_TYPE
			INTO 	:user_subsvc, :mpbx_type
			FROM 	MPX_USERPROFILE
			WHERE 	USER_ID=:user_id LIMIT 1;
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile SELECT FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_NOT_REGISTERED;
	}
	_mrs_logprint(DEBUG_3, "(db-data) USER_ID[%s], USER_SUBSVC[%d]\n", user_id, user_subsvc);

	/* USER_mPBX_TYPE */
	item = cJSON_GetObjectItem(root, "USER_MPBX_TYPE");
	// blocked by shyoun. 2016.11.15 required by heo.
	//if(!item){ print_mis_para(__func__, "USER_MPBX_TYPE"); goto no_required;}
	if (item)
	{
		if(item->type == cJSON_Number) mpbx_type = item->valueint; else mpbx_type = atoi(item->valuestring);
		_mrs_logprint(DEBUG_5, "USER_mPBX_TYPE -------------->[%d]\n", mpbx_type);
		if(mpbx_type < 1 || mpbx_type > 3)
		{
			_mrs_logprint(DEBUG_5, "Invalid USER_mPBX_TYPE Value -------------------->[%d]\n", mpbx_type);
			 return ERR_INVALID_DATA;
		}
	}

	/* USER_SUBSVC */
	while (1)
	{
		if (subSvcList[i].key == 0)
			break;

		item = cJSON_GetObjectItem(root, subSvcList[i].svc_name);
		if(item){
			_mrs_logprint(DEBUG_5, "SVC=[%s] STATUS=[%s] INDEX=[%d]\n", subSvcList[i].svc_name, item->valuestring, subSvcList[i].svc_idx_num);
			if(item->type == cJSON_Number)
				user_subsvc[subSvcList[i].svc_idx_num] = item->valueint+'0';
			else
				user_subsvc[subSvcList[i].svc_idx_num] = item->valuestring[0];	//0 or 1만 들어올테니..

			item=NULL;
		}
		i++;
	}
	_mrs_logprint(DEBUG_5, "USER_SUBSVC ----------------->[%s] USER_ID=[%s]\n", user_subsvc, user_id);
	_mrs_sys_datestring_sec(chg_date);

	/* MPX_USERPROFILE Table에 UPDATE */
	/*
	EXEC SQL AT :sessionid UPDATE MPX_USERPROFILE SET USER_STATUS=:user_state, MOBILE_NUM=:m_num, MSP=:mpbx_msp, EXT_NUM=:ext_num, USER_NAME=:user_name, USER_EMAIL=:user_email, USER_PWD=:user_pwd, USER_mPBX_TYPE=:mpbx_type, MULTI_DEV_KEY=:multi_dev_key, DEV_TYPE=:dev_type, DEV_OS=:dev_os, DEV_OS_VER=:dev_os_ver, DEV_APP_VER=:dev_app_ver, DEV_MODEL=:dev_model, USER_SUBSVC=:sub_svc, URI=:uri, URI_TYPE=:uri_type, INSERTDATE=:ins_date, UPDATEDATE=:chg_date WHERE BIZ_PLACE_CODE=:biz_place_code AND USER_ID=:user_id;
	*/
	/* 2016.07.19 v0.99 */
	EXEC SQL AT :sessionid
			UPDATE 	MPX_USERPROFILE
			SET 	USER_mPBX_TYPE=:mpbx_type, USER_SUBSVC=:user_subsvc
			WHERE 	USER_ID=:user_id;
	if(sqlca.sqlcode != SQL_SUCCESS)
	{
		_mrs_logprint(DEBUG_2, " MPBX PROV - MPX_Userprofile UPDATE FAIL. USER_ID[%s] - [%s][%d] %s\n", user_id, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);
		return ERR_DB_HANDLING;
	}

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

/* 사용자 정보 삭제 */
int prov_proc_del_user(int nid, char *sessionid, cJSON *root)
{
	EXEC SQL BEGIN DECLARE SECTION;
	char	user_id[24+1];
	char	biz_place_code[5+1];
	EXEC SQL END DECLARE SECTION;

	cJSON 	*item=NULL;

	if(!root)
		return ERR_UNEXPECTED;

	memset(user_id,			0x00, sizeof(user_id));
	memset(biz_place_code,	0x00, sizeof(biz_place_code));

	/* USER_KEYID */
	item = cJSON_GetObjectItem(root, "USER_KEYID");
	if(!item){ print_mis_para(__func__, "USER_KEYID"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "USER_KEYID", item->valuestring); return ERR_INVALID_DATA;}
	if(compare_chr_len(strlen(item->valuestring), sizeof(user_id), "USER_KEY_ID", item->valuestring)<0){return ERR_INVALID_DATA;}
	strcpy(user_id, item->valuestring);
	_mrs_logprint(DEBUG_5, "USER_KEY_ID ----------------->[%s]\n", user_id);
	item=NULL;

	#ifdef __NOTUSE_20170922_HEO
		/* START, ver1.0.7, 20170928 delete */
		/* BIZ_PLACE_CODE */
		item = cJSON_GetObjectItem(root, "BIZ_PLACE_CODE");

		/* START, 20170922 
		modified by dduckk
		do not check BIZ_PLACE_CODE */
		//if(!item){ print_mis_para(__func__, "BIZ_PLACE_CODE"); goto no_required;}
		if(item && strlen(item->valuestring)> 0){_mrs_logprint(DEBUG_5, "0. BIZ_PLACE_CODE -------------->[%s]\n", item->valuestring);}
		/* END, 20170922 */

		/* START, ver1.0.5 modify null */
		//if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "BIZ_PLACE_CODE", item->valuestring); return ERR_INVALID_DATA;}
		/* END, ver1.0.5 modify null */
		if (strlen (item->valuestring) > (sizeof (biz_place_code) -1))
		{
			_mrs_logprint(DEBUG_1, "BIZ_PLACE_CODE(%s) TOO LONG. ACCEPTED LENGTH (%d) -------------->[%s]\n", item->valuestring, sizeof (biz_place_code) -1);
			return ERR_INVALID_DATA;
		}
		strcpy(biz_place_code, item->valuestring);
		_mrs_logprint(DEBUG_5, "1. BIZ_PLACE_CODE -------------->[%s]\n", biz_place_code);
		item=NULL;
		/* END, ver1.0.7, 20170928 delete */
	#endif

	/* START, Ver 1.0.3, 20170215 modify swhan  */
	/*
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USERPROFILE
			WHERE 	BIZ_PLACE_CODE=:biz_place_code AND
					USER_ID=:user_id;
	*/
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USERPROFILE
			WHERE 	USER_ID=:user_id;
	/* END, 20170215 modify swhan  */
	if (sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA) /* check sqlca.sqlcode */
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s], USER_ID[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, user_id, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	_mrs_logprint(DEBUG_4,
			"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USERPROFILE WHERE BIZ_PLACE_CODE[%s], USER_ID[%s]\n",
			nid, sessionid, biz_place_code, user_id);

	/* START, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */
	/* 사용자별 휴일관리 삭제 */
	EXEC SQL AT :sessionid
			DELETE
			FROM 	MPX_USER_HOLIDAY
			WHERE 	USER_ID = :user_id;
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_HOLIDAY WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_4,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_CUSTHOLIDAY WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}

	/* 사용자별 근무시간 삭제 */
	EXEC SQL AT :sessionid
				DELETE
				FROM 	MPX_USER_WORKTIME
				WHERE 	USER_ID = :user_id;
	if(sqlca.sqlcode != SQL_SUCCESS && sqlca.sqlcode != SQL_NO_DATA)
	{
		_mrs_logprint(DEBUG_2,
				"????? nid[%d] s_id(%s) MPBX_PROV DELETE FAIL - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s] - [%d][%s]\n",
				nid, sessionid, biz_place_code, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	else
	{
		_mrs_logprint(DEBUG_4,
				"nid[%d] s_id(%s) MPBX_PROV DELETE SUCCESS - MPX_USER_WORKTIME WHERE BIZ_PLACE_CODE[%s]\n",
				nid, sessionid, biz_place_code);
	}
	/* END, Ver 1.1.4, add delete_user_holiday, delete_user_worktime */

	return PROV_SUCCESS;

no_required:
	return ERR_MISSING_PARAMETER;
}

int db_disconnect(int nid, char* sessionid)
{
	/* rollback */
	EXEC SQL AT :sessionid ROLLBACK;
	/* disconnect */
	EXEC SQL AT :sessionid DISCONNECT;

	if (sqlca.sqlcode != SQL_SUCCESS) /* check sqlca.sqlcode */
	{
		_mrs_logprint(DEBUG_2,
				" ??? nid[%d]  s_id[%s] Disconnection Fail [%d]-[%s]\n",
				nid, sessionid, SQLCODE, sqlca.sqlerrm.sqlerrmc);

		return ERR_DB_HANDLING;
	}
	_mrs_logprint(DEBUG_6, "S_ID[%s] Disconnect OK !!!!\n", sessionid);

	return 0;
}

