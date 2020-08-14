package com.trendy.job;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import weaver.conn.RecordSet;
import weaver.conn.RecordSetDataSource;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.company.SubCompanyComInfo;
import weaver.interfaces.schedule.BaseCronJob;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 同步组织架构，读第三方表数据
 * shu 20200803
 */
public class HrSyncJob extends BaseCronJob {
    static private Log log = LogFactory.getLog(HrSyncJob.class);

    static private boolean outSyncLog = false;      // 打印同步日志
    static private String dataBase_dataSource = "HRDBConnection";//ps系统数据源

    public static boolean isOutSyncLog() {
        return outSyncLog;
    }

    public static void setOutSyncLog(boolean outSyncLog) {
        HrSyncJob.outSyncLog = outSyncLog;
    }

    static public void getSettings() {
        log.info(" ---------------------------------------- ");
        log.info("\t打印同步日志 : " + outSyncLog);
        log.info(" ---------------------------------------- ");
    }

    @Override
    public void execute() {
        long time;
        log.info("=== 同步组织架构任务 开始===");

        HrSyncJob.setOutSyncLog(true);

        log.info("=== 同步分部 开始===");
        time = System.currentTimeMillis();
        synSubCompany();
        time = System.currentTimeMillis() - time;
        log.info("=== 同步分部 结束  总耗时 : " + (time / 1000) + "秒 ===");

        log.info("=== 同步部门 开始===");
        time = System.currentTimeMillis();
        synDepartment();
        time = System.currentTimeMillis() - time;
        log.info("=== 同步部门 结束  总耗时 : " + (time / 1000) + "秒 ===");

        removeCache();//清理缓存

        log.info("=== 同步岗位 开始===");
        time = System.currentTimeMillis();
        syncJobTitle();
        time = System.currentTimeMillis() - time;
        log.info("=== 同步岗位 结束  总耗时 : " + (time / 1000) + "秒 ===");

        log.info("=== 同步人员 开始===");
        time = System.currentTimeMillis();
        syncHrmResource();
        time = System.currentTimeMillis() - time;
        log.info("=== 同步人员 结束  总耗时 : " + (time / 1000) + "秒 ===");

        /*log.info("=== 同步矩阵 开始===");
        time = System.currentTimeMillis();
        time = System.currentTimeMillis() - time;
        log.info("=== 同步矩阵 结束  总耗时 : " + (time / 1000) + "秒 ===");*/

        log.info("=== 同步组织架构任务 结束 ===");
    }

    /**
     * 同步分部
     */
    static public void synSubCompany() {
        try {
            RecordSetDataSource ps_rs = new RecordSetDataSource(dataBase_dataSource);
            String ps_sql;
            ps_sql = "select * from SYSADM.PS_T_DEPT_CHAIN_VW where T_DEPTLVL_CUR = 'LV1'";//表示分部
            ps_rs.execute(ps_sql);
            while (ps_rs.next()) {
                String DEPTID = Util.null2String(ps_rs.getString("DEPTID"));//分部编码
                String EFFDT = Util.null2String(ps_rs.getString("EFFDT"));//创建时间？？
                String TREE_NAME = Util.null2String(ps_rs.getString("TREE_NAME"));//？
                String T_DEPTLVL_CUR = Util.null2String(ps_rs.getString("T_DEPTLVL_CUR"));//级别  分部 LV1
                String T_DEPT_JT = Util.null2String(ps_rs.getString("T_DEPT_JT"));//集团编码，不用处理
                String T_DEPT_LV1 = Util.null2String(ps_rs.getString("T_DEPT_LV1"));//所属分部，不用处理
                String T_DEPT_LV1_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV1_DESC"));//分部名称

                String sql;
                RecordSet rs = new RecordSet();
                //处理分部信息
                //上级分部id
                String supSubId = "";//上级分部 为空
                //分部id
                String subComId = "";//分部id
                sql = "select id from hrmsubcompany where subcompanycode = '" + DEPTID + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    subComId = Util.null2String(rs.getString("id"));
                } else {//创建一个分部
                    subComId = createSubCom(DEPTID, T_DEPT_LV1_DESC, supSubId);
                }
                sql = " update hrmsubcompany set ";
                sql += " subcompanyname = '" + T_DEPT_LV1_DESC + "' ";
                sql += " ,subcompanydesc = '" + T_DEPT_LV1_DESC + "' ";
                sql += " ,modified = '" + TimeUtil.getCurrentTimeString() + "' ";
                sql += " where id = '" + subComId + "' ";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("同步分部信息 出错");
            log.error(sw.toString());
        }
    }

    /**
     * 同步部门
     */
    static public void synDepartment() {
        try {
            RecordSetDataSource ps_rs = new RecordSetDataSource(dataBase_dataSource);
            String ps_sql;
            ps_sql = "select * from SYSADM.PS_T_DEPT_CHAIN_VW where T_DEPTLVL_CUR <> 'LV1'";//表示不等于分部
            ps_sql += " order by T_DEPTLVL_CUR ";//从LV2开始同步
            ps_rs.execute(ps_sql);
            while (ps_rs.next()) {
                String SETID = Util.null2String(ps_rs.getString("SETID"));//?
                String DEPTID = Util.null2String(ps_rs.getString("DEPTID"));//编码
                String EFFDT = Util.null2String(ps_rs.getString("EFFDT"));//创建时间？
                String TREE_NAME = Util.null2String(ps_rs.getString("TREE_NAME"));//？
                String T_DEPTLVL_CUR = Util.null2String(ps_rs.getString("T_DEPTLVL_CUR"));//LV等级
                String T_DEPT_JT = Util.null2String(ps_rs.getString("T_DEPT_JT"));//集团
                String T_DEPT_LV1 = Util.null2String(ps_rs.getString("T_DEPT_LV1"));//分部编码
                String T_DEPT_LV2 = Util.null2String(ps_rs.getString("T_DEPT_LV2"));//部门编码
                String T_DEPT_LV3 = Util.null2String(ps_rs.getString("T_DEPT_LV3"));//部门编码
                String T_DEPT_LV4 = Util.null2String(ps_rs.getString("T_DEPT_LV4"));//部门编码
                String T_DEPT_LV5 = Util.null2String(ps_rs.getString("T_DEPT_LV5"));//部门编码
                String T_DEPT_LV6 = Util.null2String(ps_rs.getString("T_DEPT_LV6"));//部门编码
                String T_DEPT_LV7 = Util.null2String(ps_rs.getString("T_DEPT_LV7"));//部门编码
                String T_DEPT_JT_DESC = Util.null2String(ps_rs.getString("T_DEPT_JT_DESC"));//集团名称
                String T_DEPT_LV1_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV1_DESC"));//分部名称
                String T_DEPT_LV2_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV2_DESC"));//部门名称
                String T_DEPT_LV3_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV3_DESC"));//部门名称
                String T_DEPT_LV4_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV4_DESC"));//部门名称
                String T_DEPT_LV5_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV5_DESC"));//部门名称
                String T_DEPT_LV6_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV6_DESC"));//部门名称
                String T_DEPT_LV7_DESC = Util.null2String(ps_rs.getString("T_DEPT_LV7_DESC"));//部门名称

                String sql;
                RecordSet rs = new RecordSet();
                //分部
                String subComId = "";
                sql = "select id from hrmsubcompany where subcompanycode = '" + T_DEPT_LV1 + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    subComId = Util.null2String(rs.getString("id"));//分部id
                }

                //部门处理  上级部门
                //处理逻辑
                //部门表中记录1~7级部门编码，根据当前部门的级别，从当前部门的上一级别部门编码开始进行非空判断，若非空则取该部门编码作为上级部门的编码；若为空则继续判断再上一级部门编码字段，找到非空的部门编码作为上级部门的编码。
                String supDeptId = "";//上级部门id
                String supDeptCode = "";//上级部门编码
                String deptName = "";
                if ("LV2".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV2_DESC;//部门名称
                    supDeptCode = "";
                } else if ("LV3".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV3_DESC;
                    if (!"".equals(T_DEPT_LV2)) {
                        supDeptCode = T_DEPT_LV2;
                    }
                } else if ("LV4".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV4_DESC;//部门名称
                    if (!"".equals(T_DEPT_LV3)) {
                        supDeptCode = T_DEPT_LV3;
                    } else {
                        if (!"".equals(T_DEPT_LV2)) {
                            supDeptCode = T_DEPT_LV2;
                        }
                    }
                } else if ("LV5".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV5_DESC;//部门名称
                    if (!"".equals(T_DEPT_LV4)) {
                        supDeptCode = T_DEPT_LV4;
                    } else {
                        if (!"".equals(T_DEPT_LV3)) {
                            supDeptCode = T_DEPT_LV3;
                        } else {
                            if (!"".equals(T_DEPT_LV2)) {
                                supDeptCode = T_DEPT_LV2;
                            }
                        }
                    }
                } else if ("LV6".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV6_DESC;//部门名称
                    if (!"".equals(T_DEPT_LV5)) {
                        supDeptCode = T_DEPT_LV5;
                    } else {
                        if (!"".equals(T_DEPT_LV4)) {
                            supDeptCode = T_DEPT_LV4;
                        } else {
                            if (!"".equals(T_DEPT_LV3)) {
                                supDeptCode = T_DEPT_LV3;
                            } else {
                                if (!"".equals(T_DEPT_LV2)) {
                                    supDeptCode = T_DEPT_LV2;
                                }
                            }
                        }
                    }
                } else if ("LV7".equals(T_DEPTLVL_CUR)) {
                    deptName = T_DEPT_LV7_DESC;//部门名称
                    if (!"".equals(T_DEPT_LV6)) {
                        supDeptCode = T_DEPT_LV6;
                    } else {
                        if (!"".equals(T_DEPT_LV5)) {
                            supDeptCode = T_DEPT_LV5;
                        } else {
                            if (!"".equals(T_DEPT_LV4)) {
                                supDeptCode = T_DEPT_LV4;
                            } else {
                                if (!"".equals(T_DEPT_LV3)) {
                                    supDeptCode = T_DEPT_LV3;
                                } else {
                                    if (!"".equals(T_DEPT_LV2)) {
                                        supDeptCode = T_DEPT_LV2;
                                    }
                                }
                            }
                        }
                    }
                }
                //上级部门编码处理完毕
                if ("".equals(supDeptCode)) {
                    supDeptId = "";
                } else {
                    sql = "select id from hrmdepartment where departmentcode = '" + supDeptCode + "'";
                    if (outSyncLog) log.info(sql);
                    rs.execute(sql);
                    if (rs.next()) {
                        supDeptId = Util.null2String(rs.getString("id"));
                    }
                }//上级部门id获取完成

                //部门id
                String deptId = "";
                sql = "select id from hrmdepartment where departmentcode = '" + DEPTID + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    deptId = Util.null2String(rs.getString("id"));
                } else {//创建一个部门
                    deptId = createDept(DEPTID, deptName, deptName, supDeptId, subComId);
                }

                sql = " update hrmdepartment set ";
                sql += " departmentname = '" + deptName + "' ";
                sql += " ,departmentmark = '" + deptName + "' ";
                sql += " ,modified = '" + TimeUtil.getCurrentTimeString() + "' ";
                sql += " where id = '" + deptId + "' ";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("同步部门信息 出错");
            log.error(sw.toString());
        }
    }

    /**
     * 同步岗位
     */
    static public void syncJobTitle() {
        try {
            RecordSetDataSource ps_rs = new RecordSetDataSource(dataBase_dataSource);
            String ps_sql;
            ps_sql = "select * from SYSADM.PS_T_JOBCODE_VW";
            ps_rs.execute(ps_sql);
            while (ps_rs.next()) {
                String JOBCODE = Util.null2String(ps_rs.getString("JOBCODE"));//岗位code
                String JOBCODE_DESCR = Util.null2String(ps_rs.getString("JOBCODE_DESCR"));//岗位名称
                String id = gethrmjobtitlesid(JOBCODE_DESCR, JOBCODE);
                RecordSet rs = new RecordSet();
                String sql;
                sql = " update hrmjobtitles set ";
                sql += " jobtitlename = '" + JOBCODE_DESCR + "' ";
                sql += " ,jobtitlemark = '" + JOBCODE_DESCR + "' ";
                sql += " ,jobtitleremark = '" + JOBCODE_DESCR + "' ";
                sql += " where id = '" + id + "' ";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("同步岗位信息 出错");
            log.error(sw.toString());
        }
    }

    /**
     * 同步人员
     */
    static public void syncHrmResource() {
        try {
            RecordSetDataSource ps_rs = new RecordSetDataSource(dataBase_dataSource);
            String ps_sql;
            //ps_sql = "select * from SYSADM.PS_T_EE_INF_ALL where HR_STATUS = 'A' order by HIRE_DT";//按照入职日期先后
            ps_sql = "select * from SYSADM.PS_T_EE_INF_ALL order by HIRE_DT";//按照入职日期先后
            ps_rs.execute(ps_sql);
            while (ps_rs.next()) {//字段很多，待后续跟进自定义表
                String EMPLID = Util.null2String(ps_rs.getString("EMPLID"));//员工编号 对应workcode
                String EFFDT = Util.null2String(ps_rs.getString("EFFDT"));//最新异动日期
                String NAME = Util.null2String(ps_rs.getString("NAME"));//姓名
                String NAME_AC = Util.null2String(ps_rs.getString("NAME_AC"));//别名
                String HR_STATUS = Util.null2String(ps_rs.getString("HR_STATUS"));//入/离职状态
                String SEX = Util.null2String(ps_rs.getString("SEX"));//性别
                String COMPANY = Util.null2String(ps_rs.getString("COMPANY"));//公司代码
                String COMPANY_DESCR = Util.null2String(ps_rs.getString("COMPANY_DESCR"));//公司描述
                String DEPTID = Util.null2String(ps_rs.getString("DEPTID"));//部门代码
                String T_DEPT_DESCR = Util.null2String(ps_rs.getString("T_DEPT_DESCR"));//部门描述
                String JOBCODE = Util.null2String(ps_rs.getString("JOBCODE"));//职务代码
                String JOBCODE_DESCR = Util.null2String(ps_rs.getString("JOBCODE_DESCR"));//职务描述
                String POSITION_NBR = Util.null2String(ps_rs.getString("POSITION_NBR"));//职位代码
                String POSITION_NBR_DESCR = Util.null2String(ps_rs.getString("POSITION_NBR_DESCR"));//职位描述
                String LOCATION = Util.null2String(ps_rs.getString("LOCATION"));//办公地点代码
                String LOCATION_DESCR = Util.null2String(ps_rs.getString("LOCATION_DESCR"));//办公地点描述
                String MOBILE_PHONE = Util.null2String(ps_rs.getString("MOBILE_PHONE"));//手机号
                String PHONE = Util.null2String(ps_rs.getString("PHONE"));//电话
                String EMAIL_ADDR = Util.null2String(ps_rs.getString("EMAIL_ADDR"));//邮箱  截取 @ 前面作为loginid
                String T_SHOP_ID = Util.null2String(ps_rs.getString("T_SHOP_ID"));//店铺编号
                String T_ADDRESS_C = Util.null2String(ps_rs.getString("T_ADDRESS_C"));//店铺地址
                String T_BRAND = Util.null2String(ps_rs.getString("T_BRAND"));//品牌编号
                String T_BRAND_DESCR = Util.null2String(ps_rs.getString("T_BRAND_DESCR"));//品牌描述
                String T_REGION = Util.null2String(ps_rs.getString("T_REGION"));//区域代码
                String T_REGION200 = Util.null2String(ps_rs.getString("T_REGION200"));//区域描述
                String T_STATE = Util.null2String(ps_rs.getString("T_STATE"));//省代码
                String STATE_DESCR2 = Util.null2String(ps_rs.getString("STATE_DESCR2"));//省描述
                String T_CITY = Util.null2String(ps_rs.getString("T_CITY"));//城市代码
                String T_CITY200 = Util.null2String(ps_rs.getString("T_CITY200"));//城市描述
                String T_COST_CENTRE = Util.null2String(ps_rs.getString("T_COST_CENTRE"));//成本中心编号
                String DESCR200 = Util.null2String(ps_rs.getString("DESCR200"));//成本中心描述
                String SUPV_LVL_ID = Util.null2String(ps_rs.getString("SUPV_LVL_ID"));//级别代码
                String DESCR4 = Util.null2String(ps_rs.getString("DESCR4"));//级别描述
                String T_EMP_LVL = Util.null2String(ps_rs.getString("T_EMP_LVL"));//层级代码
                String T_EMP_LVL_DESC = Util.null2String(ps_rs.getString("T_EMP_LVL_DESC"));//层级描述
                String BUSINESS_UNIT = Util.null2String(ps_rs.getString("BUSINESS_UNIT"));//业务单元代码
                String BUSINESS_DESCR = Util.null2String(ps_rs.getString("BUSINESS_DESCR"));//业务单元描述
                String T_WRK_TYPE = Util.null2String(ps_rs.getString("T_WRK_TYPE"));//工作性质代码
                String DESCR30 = Util.null2String(ps_rs.getString("DESCR30"));//工作性质描述
                String ACCOUNT_EC_ID = Util.null2String(ps_rs.getString("ACCOUNT_EC_ID"));//银行账号
                String ACCOUNT_NAME = Util.null2String(ps_rs.getString("ACCOUNT_NAME"));//户名
                String BANK_CD = Util.null2String(ps_rs.getString("BANK_CD"));//银行代码
                String BANK_NM = Util.null2String(ps_rs.getString("BANK_NM"));//银行名称
                String EMPLID_IPOS = Util.null2String(ps_rs.getString("EMPLID_IPOS"));//POS编号
                String BIRTHDATE = Util.null2String(ps_rs.getString("BIRTHDATE"));//出生日期
                String MAR_STATUS = Util.null2String(ps_rs.getString("MAR_STATUS"));//婚姻状态代码
                String DESCR3 = Util.null2String(ps_rs.getString("DESCR3"));//婚姻状态描述
                String T_HIGHEST_EDUC_LVL = Util.null2String(ps_rs.getString("T_HIGHEST_EDUC_LVL"));//学历
                String DESCR50 = Util.null2String(ps_rs.getString("DESCR50"));//民族
                String NATIONAL_ID = Util.null2String(ps_rs.getString("NATIONAL_ID"));//证件号号
                String DESCR6 = Util.null2String(ps_rs.getString("DESCR6"));//证件类型
                String CONTACT_NAME = Util.null2String(ps_rs.getString("CONTACT_NAME"));//紧急联系人姓名
                String CONTACT_PHONE = Util.null2String(ps_rs.getString("CONTACT_PHONE"));//紧急联系人电话
                String BIRTHCOUNTRY = Util.null2String(ps_rs.getString("BIRTHCOUNTRY"));//国家代码
                String COUNTRY_DESCR = Util.null2String(ps_rs.getString("COUNTRY_DESCR"));//国家描述
                String ADDRESSLONG1 = Util.null2String(ps_rs.getString("ADDRESSLONG1"));//紧急联系人地址1
                String ADDRESSLONG2 = Util.null2String(ps_rs.getString("ADDRESSLONG2"));//紧急联系人地址2
                String SUPERVISOR_ID = Util.null2String(ps_rs.getString("SUPERVISOR_ID"));//上司员工编号
                String HIRE_DT = Util.null2String(ps_rs.getString("HIRE_DT"));//入职日期
                String START_DT_CHN = Util.null2String(ps_rs.getString("START_DT_CHN"));//开始工作日期
                String GPCN_ACOUNT_NUM = Util.null2String(ps_rs.getString("GPCN_ACOUNT_NUM"));//社保账号
                String GPCN_FILE_NO = Util.null2String(ps_rs.getString("GPCN_FILE_NO"));//公积金账号
                String FLAG2 = Util.null2String(ps_rs.getString("FLAG2"));//师兄师姐标识
                String EMPLID_AUTHOR = Util.null2String(ps_rs.getString("EMPLID_AUTHOR"));//HR薪酬联系人
                String DISABLED = Util.null2String(ps_rs.getString("DISABLED"));//
                String STATE = Util.null2String(ps_rs.getString("STATE"));//开户行省代码
                String STATE_DESCR = Util.null2String(ps_rs.getString("STATE_DESCR"));//开户行省描述
                String CITY = Util.null2String(ps_rs.getString("CITY"));//开户行城市代码
                String CITY_ITA = Util.null2String(ps_rs.getString("CITY_ITA"));//开户行城市描述
                String T_BANK_NAME = Util.null2String(ps_rs.getString("T_BANK_NAME"));//开户行名称

                //处理人员信息
                String sql;
                RecordSet rs = new RecordSet();

                //获取部门、分部id
                String departmentid = "0";//默认
                String subcompanyid1 = "0";//默认
                sql = "select id,subcompanyid1 from hrmdepartment where departmentcode = '" + DEPTID + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    departmentid = Util.null2String(rs.getString("id"));
                    subcompanyid1 = Util.null2String(rs.getString("subcompanyid1"));
                }
                //性别
                String sex = "0";//男
                if ("F".equals(SEX)) {//女
                    sex = "1";
                }
                //状态
                String status = "";
                if ("A".equals(HR_STATUS)) {
                    status = "1";//在职
                } else if ("I".equals(HR_STATUS)) {
                    status = "5";//离职
                } else {
                    status = "7";//无效
                }
                //登录名
                String loginid = "";
                if (EMAIL_ADDR.length() > 0) {
                    if (EMAIL_ADDR.contains("@")) {
                        loginid = EMAIL_ADDR.substring(0, EMAIL_ADDR.indexOf("@"));
                    }
                }
                //岗位id
                String jobtitle = "0";
                sql = "select id from hrmjobtitles where jobtitlecode = '" + JOBCODE + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    jobtitle = Util.null2String(rs.getString("id"));
                }
                //密码
                String password = "1";
                password = stringToMD5(password).toUpperCase();//转大写
                //上级id
                String managerid = "0";//没有上级就  0
                sql = "select id from hrmresource where workcode = '" + SUPERVISOR_ID + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    managerid = Util.null2String(rs.getString("id"));
                }
                //人员id
                String hrmId = "";
                sql = "select id from hrmresource where workcode = '" + EMPLID + "'";
                if (outSyncLog) log.info(sql);
                rs.execute(sql);
                if (rs.next()) {
                    hrmId = Util.null2String(rs.getString("id"));
                } else {
                    hrmId = createHrm(EMPLID);
                }

                /*log.info("BIRTHDATE:" + BIRTHDATE);
                log.info("BIRTHDATE:" + HIRE_DT);
                log.info("BIRTHDATE:" + START_DT_CHN);*/

                //处理信息
                sql = "update hrmresource set ";
                sql += " modified = '" + TimeUtil.getCurrentTimeString() + "' ";
                sql += " ,lastname = '" + NAME + "' ";
                sql += " ,status = '" + status + "' ";
                sql += " ,sex = '" + sex + "' ";
                sql += " ,jobtitle = '" + jobtitle + "' ";
                sql += " ,workroom = '" + LOCATION_DESCR + "' ";
                sql += " ,mobile = '" + MOBILE_PHONE + "' ";
                sql += " ,telephone = '" + PHONE + "' ";
                sql += " ,email = '" + EMAIL_ADDR + "' ";
                sql += " ,accountid1 = '" + ACCOUNT_EC_ID + "' ";
                sql += " ,birthday = '" + formatDateString(BIRTHDATE) + "' ";
                sql += " ,degree = '" + T_HIGHEST_EDUC_LVL + "' ";
                sql += " ,folk = '" + DESCR50 + "' ";
                sql += " ,certificatenum = '" + NATIONAL_ID + "' ";
                sql += " ,managerid = '" + managerid + "' ";
                sql += " ,createdate = '" + formatDateString(HIRE_DT) + "' ";
                sql += " ,startdate = '" + formatDateString(START_DT_CHN) + "' ";
                sql += " ,accumfundaccount = '" + GPCN_FILE_NO + "' ";
                sql += " ,departmentid = '" + departmentid + "' ";
                sql += " ,subcompanyid1 = '" + subcompanyid1 + "' ";
                sql += " ,loginid = '" + loginid + "' ";
                sql += " ,password = '" + password + "' ";
                sql += " where id = '" + hrmId + "' ";
                //剩下安全级别
                if (outSyncLog) log.info(sql);
                rs.execute(sql);

            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("同步人员信息 出错");
            log.error(sw.toString());
        }
    }


    /**
     * 清理缓存
     */
    static public void removeCache() {
        log.info("清理分部、部门缓存开始");
        try {
            SubCompanyComInfo dci = new SubCompanyComInfo();
            DepartmentComInfo dcit = new DepartmentComInfo();
            dci.removeCache();
            dcit.removeCache();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error(sw.toString());
        }
        log.info("清理分部、部门缓存结束");
    }

    /**
     * 创建分部，获取分部id
     *
     * @param subcompanycode 分部编码
     * @param subcompanyname 分部名称
     * @param supsubcomid    上级分部id
     * @return 分部id
     */
    static private String createSubCom(String subcompanycode, String subcompanyname, String supsubcomid) {
        String sql;
        RecordSet rs = new RecordSet();
        String comID = "";
        if ("".equals(supsubcomid)) {
            supsubcomid = "0";//表示上级是集团
        }
        sql = "insert into hrmsubcompany(subcompanyname,supsubcomid,subcompanycode,created,modified)";
        sql += " values('" + subcompanyname + "','" + supsubcomid + "','" + subcompanycode + "','" + TimeUtil.getCurrentTimeString() + "','" + TimeUtil.getCurrentTimeString() + "') ";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        sql = "select id from hrmsubcompany where subcompanycode = '" + subcompanycode + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            comID = Util.null2String(rs.getString("id"));
        }
        return comID;
    }

    /**
     * 创建部门信息  返回部门id
     *
     * @param departmentcode 部门编码
     * @param departmentname 部门名称
     * @param supdepid       上级id
     * @param subcompanyid1  所属分部id
     * @return 部门id
     */
    static private String createDept(String departmentcode, String departmentname, String departmentmark, String supdepid, String subcompanyid1) {
        String sql;
        RecordSet rs = new RecordSet();
        String deptId = "";
        if ("".equals(supdepid)) {
            supdepid = "0";//表示没有上级部门
        }
        sql = "insert into hrmdepartment(departmentname,departmentmark,supdepid,subcompanyid1,departmentcode,created,modified)";
        sql += " values('" + departmentname + "','" + departmentmark + "','" + supdepid + "','" + subcompanyid1 + "','" + departmentcode + "','" + TimeUtil.getCurrentTimeString() + "','" + TimeUtil.getCurrentTimeString() + "') ";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        sql = "select id from hrmdepartment where departmentcode = '" + departmentcode + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            deptId = Util.null2String(rs.getString("id"));
        }
        return deptId;
    }

    /**
     * 根据岗位名称获取岗位id
     */
    static private String gethrmjobtitlesid(String jobtitlename, String jobtitlecode) {
        String id = "";
        String sql;
        RecordSet rs = new RecordSet();

        //职务类型表
        String jobgroups_id = "";
        sql = "select * from hrmjobgroups where jobgroupname = '" + jobtitlename + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            jobgroups_id = Util.null2String(rs.getString("id"));
        } else {
            sql = "insert into hrmjobgroups(jobgroupname,jobgroupremark) values('" + jobtitlename + "','" + jobtitlename + "')";
            if (outSyncLog) log.info(sql);
            rs.execute(sql);
        }
        sql = "select * from hrmjobgroups where jobgroupname = '" + jobtitlename + "'";
        rs.execute(sql);
        if (rs.next()) {
            jobgroups_id = Util.null2String(rs.getString("id"));
        }

        sql = "update hrmjobgroups set ";
        sql += " jobgroupname = '" + jobtitlename + "' ";
        sql += " ,jobgroupremark = '" + jobtitlename + "' ";
        sql += " where id = '" + jobgroups_id + "' ";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);

        //职责表
        String jobactivities_id = "";
        sql = "select * from hrmjobactivities where jobactivityname = '" + jobtitlename + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            jobactivities_id = Util.null2String(rs.getString("id"));
        } else {
            sql = "insert into hrmjobactivities(jobactivityname,jobactivitymark,jobgroupid) values('" + jobtitlename + "','" + jobtitlename + "','" + jobgroups_id + "')";
            if (outSyncLog) log.info(sql);
            rs.execute(sql);
        }
        sql = "select * from hrmjobactivities where jobactivityname = '" + jobtitlename + "'";
        rs.execute(sql);
        if (rs.next()) {
            jobactivities_id = Util.null2String(rs.getString("id"));
        }


        sql = "select id from hrmjobtitles where jobtitlecode = '" + jobtitlecode + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            id = Util.null2String(rs.getString("id"));
        } else {//添加信息

            sql = "insert into hrmjobtitles(jobtitlename,jobtitlecode,jobtitlemark,jobtitleremark) values('" + jobtitlename + "','" + jobtitlecode + "','" + jobtitlename + "','" + jobtitlename + "')";
            if (outSyncLog) log.info(sql);
            rs.execute(sql);

        }

        sql = "select id from hrmjobtitles where jobtitlecode = '" + jobtitlecode + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            id = Util.null2String(rs.getString("id"));
        }

        sql = "update hrmjobtitles set jobactivityid = '" + jobactivities_id + "' where id = '" + id + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);

        return id;
    }

    /**
     * 创建人员信息  根据wrokcode创建人员id
     */
    static private String createHrm(String workcode) {
        String sql;
        RecordSet rs = new RecordSet();
        String oaHrId = "";
        String hrmid = "0";//默认 0
        rs.execute("select max(id) id from hrmresource");
        if (rs.next()) {
            hrmid = rs.getString("id");
        }
        sql = "insert into hrmresource" +
                "(id,workcode,status,createdate,modified) " +
                " values(" +
                "'" + (Util.getIntValue(hrmid, 0) + 1) + "'," +
                "'" + workcode + "'," +
                "'1'," +
                "'" + TimeUtil.getCurrentDateString() + "'," +
                "'" + TimeUtil.getCurrentTimeString() + "'" +
                ") ";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);

        // 找出刚插入的 ID
        sql = "select max(id) newId from hrmresource where workcode='" + workcode + "'";
        if (outSyncLog) log.info(sql);
        rs.execute(sql);
        if (rs.next()) {
            oaHrId = Util.null2String(rs.getString("newId"));
        }

        sql = "select * from cus_fielddata where id = '" + oaHrId + "'";
        rs.execute(sql);
        if (!rs.next()) {
            //添加到自定义信息表
            sql = "insert into cus_fielddata(scope,scopeid,id) values('HrmCustomFieldByInfoType','-1','" + oaHrId + "')";
            if (outSyncLog) log.info(sql);
            rs.execute(sql);
        }
        return oaHrId;
    }

    /**
     * 格式化日期   例如  2017.11.25  转成   2017-11-25
     *
     * @param dateString 日期
     * @return 日期
     */
    static public String formatDateString(String dateString) {
        if (!"".equals(dateString)) {
            if (dateString.length() >= 10) {
                dateString = dateString.substring(0, 10);
                Date date = new Date(dateString.replace(".", "/").replace("-", "/"));
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                return simpleDateFormat.format(date);
            } else {
                return "";
            }
        } else {
            return "";
        }
    }

    /**
     * MD5加密
     *
     * @param plainText 文本
     * @return MD5加密文本
     */
    private static String stringToMD5(String plainText) {
        byte[] secretBytes = null;
        try {
            secretBytes = MessageDigest.getInstance("md5").digest(
                    plainText.getBytes());
        } catch (NoSuchAlgorithmException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("没有这个md5算法！");
            log.error(sw.toString());
        }
        StringBuilder md5code = new StringBuilder(new BigInteger(1, secretBytes).toString(16));
        for (int i = 0; i < 32 - md5code.length(); i++) {
            md5code.insert(0, "0");
        }
        return md5code.toString();
    }


}
