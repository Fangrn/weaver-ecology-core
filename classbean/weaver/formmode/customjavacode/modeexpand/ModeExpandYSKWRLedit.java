package weaver.formmode.customjavacode.modeexpand;

import java.util.*;
import weaver.conn.RecordSet;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.soa.workflow.request.RequestInfo;
import weaver.formmode.customjavacode.AbstractModeExpandJavaCode;


/**
 * 说明
 * 修改时
 * 类名要与文件名保持一致
 * class文件存放位置与路径保持一致。
 * 请把编译后的class文件，放在对应的目录中才能生效
 * 注意 同一路径下java名不能相同。
 * @author Administrator
 *
 */
public class ModeExpandYSKWRLedit extends AbstractModeExpandJavaCode {
	/**
	 * 执行模块扩展动作
	 * @param param
	 *  param包含(但不限于)以下数据
	 *  user 当前用户
	 */
	public void doModeExpand(Map<String, Object> param) throws Exception {
		User user = (User)param.get("user");
		int billid = -1;//数据id
		int modeid = -1;//模块id
		RequestInfo requestInfo = (RequestInfo)param.get("RequestInfo");
		if(requestInfo!=null){
			billid = Util.getIntValue(requestInfo.getRequestid());
			modeid = Util.getIntValue(requestInfo.getWorkflowid());
			if(billid>0&&modeid>0){
				RecordSet rs =new RecordSet();
				//------请在下面编写业务逻辑代码------
				rs.writeLog("已收款未认领接口开始");
				//--------------------------------获取付款单位
				String dyht="";//合同id
				String dyskx="";//对应收款项
				rs.executeQuery("select dyht,dyskx from uf_yskwrl where id=?", billid);
				if(rs.next()){
					dyht=Util.null2String(rs.getString("dyht"));
					dyskx=Util.null2String(rs.getString("dyskx"));
				}else{
					return;
				}
				
				//状态处理
				rs.executeQuery("select * from uf_httz_dt1 where mainid=? and fktia=?",dyht,dyskx);
				if(rs.next()){
					rs.executeUpdate("update uf_yskwrl set pp='0' where id=?",billid);
				}else{
					rs.executeUpdate("update uf_yskwrl set pp='2' where id=?",billid);
				}
				
				//修改合同数据
				RecordSet rs1=new RecordSet();
				rs1.executeUpdate("update uf_httz_dt1 set skzhua='1' where mainid=? and fktia=?",dyht,dyskx);
			
			}
		}
	}

}