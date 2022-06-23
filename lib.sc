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
	_conv_from_UTF8_to_eucKR(item->valuestring,strlen(item->valuestring), biz_place_name, &len_place_name);
	//strcpy(biz_place_name, item->valuestring);
	_mrs_logprint(DEBUG_5, "BIZ_PLACE_NAME -------------->[%s]\n", biz_place_name);
	item=NULL;

	/* MPBX_RN */
	item = cJSON_GetObjectItem(root, "MPBX_RN");
	if(!item){ print_mis_para(__func__, "MPBX_RN"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_RN", item->valuestring); return ERR_INVALID_DATA;}
	strcpy(mpbx_rn, item->valuestring);
	_mrs_logprint(DEBUG_5, "MPBX_RN --------------------->[%s]\n", mpbx_rn);
	item=NULL;

	/* MPBX_ACNT_NUM */
	item = cJSON_GetObjectItem(root, "MPBX_ACNT_NUM");
	if(!item){ print_mis_para(__func__, "MPBX_ACNT_NUM"); goto no_required;}
	if(strlen(item->valuestring) <= 0) { print_null_value(__func__, "MPBX_ACNT_NUM", item->valuestring); return ERR_INVALID_DATA;}
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

	/* MPX_BIZ_PLACE_INFO Table에 INSERT */
	/* START, Ver 1.0.8, 2018.02.01 */
	EXEC SQL AT :sessionid
		INSERT INTO MPX_BIZ_PLACE_INFO ( BIZ_PLACE_CODE, CUST_ID, BIZ_PLACE_NAM, MPBX_RN, ACCOUNT_NUM, BIZ_PLACE_ACCPFX, OUT_PFX,
			PBX_IP, PBX_PORT, SUBSVC, CALL_OPT, UPDATE_DATE, INS_DATE, PBX_FLAG, SHORTNUM_LEN, ACCPFX_LEN)
		VALUES( :biz_place_code, :cust_id, :biz_place_name, :mpbx_rn, :account_num, :biz_place_accpfx, :out_pfx,
			:pbx_ip, :pbx_port, :sub_svc, :call_opt, TO_CHAR (SYSDATE, 'YYYYMMDDHHMISS'), TO_CHAR (SYSDATE, 'YYYYMMDD'), :pbx_flag,
			:short_num_len, :accpfx_len);
	/* END, Ver 1.0.8, 2018.02.01 */
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

	/* 휴일 관리 */
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
			if(strlen(subitem->valuestring) > 0)
				strcpy(h_day, subitem->valuestring);
			subitem=NULL;

			/* M_TYPE */
			subitem = cJSON_GetObjectItem(arritem, "M_TYPE");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - M_TYPE"); goto no_required;}
			if(subitem->type == cJSON_Number) m_type = subitem->valueint; else m_type = atoi(subitem->valuestring);
			subitem=NULL;

			/* M_TYPE */
			subitem = cJSON_GetObjectItem(arritem, "REPEAT");
			if(!subitem){ print_mis_para(__func__, "CUST_HOLIDAY - REPEAT"); goto no_required;}
			if(subitem->type == cJSON_Number) repeat = subitem->valueint; else repeat = atoi(subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] H_DAY=[%s] : M_TYPE=[%d], REPEAT=[%d]\n", i, h_day, m_type, repeat);

			/* INSERT 휴일 관리 */
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

	/* 근무 시간 관리 */
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
			if(strlen(subitem->valuestring) > 0)
				strcpy(stime, subitem->valuestring);
			subitem=NULL;

			/* END_TIME */
			subitem = cJSON_GetObjectItem(arritem, "END_TIME");
			if(!subitem){ print_mis_para(__func__, "CUST_WORKTIME - END_TIME"); goto no_required;}
			if(strlen(subitem->valuestring) > 0)
				strcpy(etime, subitem->valuestring);
			subitem=NULL;

			_mrs_logprint(DEBUG_5, " INDEX[%d] DAY_TYPE=[%d] : START_TIME[%s], END_TIME[%s]\n", i, day_type, stime, etime);

			/* INSERT 휴일 관리 */
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
