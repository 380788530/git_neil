package com.labi.itfin.mrs.web;
dev
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.labi.audit.domain.hangup.ContactUser;
import com.labi.audit.domain.hangup.HangUpResp;
import com.labi.audit.domain.hangup.HangupInfoResp;
import com.labi.audit.domain.hangup.HangupRequ;
import com.labi.audit.service.audit.AuditHangupService;
import com.labi.dto.loanSys.ViewLoanContractType;
import com.labi.dto.loanSys.ViewLoanContractTypeParam;
import com.labi.itfin.basics.facade.pm.dto.PManageDTO;
import com.labi.itfin.basics.facade.pm.facade.PersonnelManageFacadeService;
import com.labi.itfin.bsp.facade.model.dto.ReplyBody;
import com.labi.itfin.common.constant.dto.Result;
import com.labi.itfin.common.constant.util.enums.PlatformIdEnum;
import com.labi.itfin.common.constant.util.enums.ResultEnum;
import com.labi.itfin.facade.passport.facade.PassportFacadeService;
import com.labi.itfin.mrs.enums.OrderEnum;
import com.labi.itfin.mrs.model.*;
import com.labi.itfin.mrs.service.AuthService;
import com.labi.itfin.mrs.service.MrsRedisService;
import com.labi.itfin.mrs.service.VaildAuthService;
import com.labi.itfin.mrs.utils.*;
import com.labi.mrs.dto.enums.SearchType;
import com.labi.mrs.dto.req.LGrantStatusQueryReqDto;
import com.labi.mrs.dto.req.LOrderQueryReqDto;
import com.labi.mrs.dto.req.LOrderSearchReqDto;
import com.labi.mrs.dto.req.LOrderStatusQueryReqDto;
import com.labi.mrs.dto.resp.*;
import com.labi.mrs.service.LOrderManagerApiService;
import com.labi.mrs.utils.BaseSerializable;
import com.labi.mrs.utils.PageObject;
import com.labi.service.loanSys.DubboContractService;
import com.labi.service.loanSys.FrontApiService;
import com.labi.service.userCenter.users.FRegiUserBaseInfoService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ******************************************************************
 * <p><b></b><br/>
 * <p><b>备注：</b><br/>
 * ******************************************************************
 *
 * @param @return 设定文件
 * @author lizhiyong
 * @Title:
 * @Description: TODO(这里用一句话描述这个方法的作用)
 * @return String    返回类型
 * @date 2018-01-05 15:03
 * @throws
 */
@RestController
@RequestMapping(value = "/order")
@Slf4j
@CrossOrigin
public class OrderController {

    @Reference(check = false,timeout = 6000)
    private FRegiUserBaseInfoService fRegiUserBaseInfoService;
    @Reference(check = false,timeout = 6000)
    private DubboContractService dubboContractService;
    @Reference(check = false,timeout = 6000)
    private FrontApiService frontApiService;

    @Reference(check = false,timeout = 6000)
    private PassportFacadeService passportFacadeService;
    @Reference(check = false,timeout = 6000)
    private LOrderManagerApiService lOrderManager;

    @Reference(check = false,timeout = 6000)
    private AuditHangupService auditHangupService;

    @Autowired
    private VaildAuthService vaildAuthService;

    @Autowired
    private AuthService authService;

    @Autowired
    private InitInfo initInfo;

    @Autowired
    private MrsRedisService redisService;

    @Reference(check = false,timeout = 6000)
    private PersonnelManageFacadeService personnelManageFacadeService;

//    @Reference(check = false)
//    private OrganizationService organizationService;


    @ApiOperation(value = "查询自己权限下的订单总览", notes = "")
    @RequestMapping(value = {"/queryOrderReview"}, method = RequestMethod.POST)
    public ReplyBody queryOrderReview(@Valid @RequestBody OrderReviewReqParam orderReviewParam, BindingResult bindingResult) {
        log.info("查询自己权限下的订单总览,请求参数:" + Tools.sendJson(orderReviewParam));
        if (bindingResult.hasErrors()) {
            return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
        }
        if (Tools.isEmpty(orderReviewParam.getAuthId())) {
            return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
        }
        ReplyBody replyBody = vaildAuthService.vaildAuth(orderReviewParam.getToken());
        if (0 != replyBody.getCode()) {
            return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
        }
        LOrderStatusQueryReqDto orderDto = new LOrderStatusQueryReqDto();

        List<String> businessOrgIds = new ArrayList<String>();
        String pid = redisService.get(orderReviewParam.getLoginPhone() + "pid");
        Result<List<PManageDTO>> pManageByParams = personnelManageFacadeService.getPManageByParams(null, Long.valueOf(pid), null, null, PlatformIdEnum.ORG_CRM_MERCHANT.getIndex());
        log.info("根据pid(" + pid + ")查询机构信息，后台返回：" + Tools.sendJson(pManageByParams));
        if (ResultEnum.SUCCESS.getCode()== pManageByParams.getCode()) {
            //List<PManageDTO> pManageDTOList = JSON.parseArray(pmByPrentPid.getData(), PManageDTO.class);
            if (!Tools.isEmpty(pManageByParams.getData()) && pManageByParams.getData().size() > 0) {

                for (PManageDTO pManageDTO : pManageByParams.getData()) {
                    businessOrgIds.add(pManageDTO.getOrgCode());
                }

                orderDto.setBusinessOrgIds(businessOrgIds);
                AuthReqParam authReqParam = new AuthReqParam();
                authReqParam.setAuthId(orderReviewParam.getAuthId());
                authReqParam.setLoginPhone(orderReviewParam.getLoginPhone());
                List<Map<String, AuthInfo>> orderStatusArray = authService.queryMenuStrByAuthId(authReqParam);
                log.info("查询自己权限下的订单总览,orderStatusStr未拼接：" + Tools.sendJson(orderStatusArray) + ";集合：" + orderStatusArray.size());
                StringBuilder beforeString = new StringBuilder();
                Map<String, Integer> examineResult = new HashMap<String, Integer>();
                Map<String, String> uniqueCodeResult = new HashMap<String, String>();
                if (orderStatusArray.size() > 0) {
                    for (Map<String, AuthInfo> map : orderStatusArray) {
                        for (String k : map.keySet()) {
                            beforeString.append(k).append(",");
                            examineResult.put(k, map.get(k).getId());
                            uniqueCodeResult.put(k, map.get(k).getUniqueCode());
                        }
                    }
                    String orderStatusStr = beforeString.toString().substring(0, beforeString.toString().length() - 1);
                    log.info("查询自己权限下的订单总览,orderStatusStr拼接：" + Tools.sendJson(orderStatusStr));
                    orderDto.setOrderStatusArray(orderStatusStr);
                    LOrderStatusQueryRespDto lOrderStatusQueryRespDto = lOrderManager.queryOrderReview(orderDto);
                    log.info("查询自己权限下的订单总览,后台返回结果:" + Tools.sendJson(lOrderStatusQueryRespDto));
                    if ("000".equals(lOrderStatusQueryRespDto.getRespCode())) {
                        List<LOrderStatusQueryRespDetailDtoListBean> lOrderStatusQueryRespDetailDtoListBeanList = JSON.parseArray(JSON.toJSON(lOrderStatusQueryRespDto.getlOrderStatusQueryRespDetailDtoList()).toString(), LOrderStatusQueryRespDetailDtoListBean.class);
                        List<LOrderStatusQueryRespDetailDtoListBean> respList = new ArrayList<LOrderStatusQueryRespDetailDtoListBean>();
                        for (LOrderStatusQueryRespDetailDtoListBean lOrderStatusQueryRespDetailDtoListBean : lOrderStatusQueryRespDetailDtoListBeanList) {
                            lOrderStatusQueryRespDetailDtoListBean.setUniqueCode(uniqueCodeResult.get(lOrderStatusQueryRespDetailDtoListBean.getExamineStatus()));
                            Integer examineStatus = examineResult.get(lOrderStatusQueryRespDetailDtoListBean.getExamineStatus());
                            lOrderStatusQueryRespDetailDtoListBean.setExamineStatus(String.valueOf(examineStatus));
                            respList.add(lOrderStatusQueryRespDetailDtoListBean);
                        }

                        return ResultUtil.success(respList);
                    } else {
                        return ResultUtil.error(Integer.valueOf(lOrderStatusQueryRespDto.getRespCode()), lOrderStatusQueryRespDto.getRespMesg());
                    }
                } else {
                    return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
                }
            } else {
                log.info("根据pid(" + pid + ")查询机构信息为空");
                return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
            }
        } else {
            return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), pManageByParams.getMessage());
        }

    }


    @ApiOperation(value = "合同展示", notes = "")
    @RequestMapping(value = {"/afOrderContract"}, method = RequestMethod.POST)
    public ReplyBody<AfOrderContractRespParam> afOrderContract(@RequestBody ContractReqParam contractReqParam) {
        log.info("合同展示,请求参数：" + Tools.sendJson(contractReqParam));
        ReplyBody replyBody = vaildAuthService.vaildAuth(contractReqParam.getToken());
        if (0 != replyBody.getCode()) {
            return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
        }
        ViewLoanContractTypeParam model = new ViewLoanContractTypeParam();
        model.setLoanId(contractReqParam.getLoanId());
        log.info("合同展示用户：" + contractReqParam.getLoginPhone() + "-->>getViewContractType--下单后查看合同链接接口 传入参数：" + Tools.sendJson(model));
        List<ViewLoanContractType> result = dubboContractService.getViewLoanContractType(model);
        log.info("合同展示用户：" + contractReqParam.getLoginPhone() + "-->>getViewContractType--下单后查看合同链接接口 后台返回：" + Tools.sendJson(result));

        List<OrContractModel> contractList = new ArrayList<OrContractModel>();
        for (ViewLoanContractType viewLoanContractType : result) {
            OrContractModel orModel = new OrContractModel();
            orModel.setName(viewLoanContractType.getContractName());
            if (viewLoanContractType.DOWNLOAD_TYPE_9F.equals(viewLoanContractType.getDownloadType())) {
                orModel.setUrl(viewLoanContractType.getDownloadPath());
            } else if (viewLoanContractType.DOWNLOAD_TYPE_LABI.equals(viewLoanContractType.getDownloadType())) {
                String contractKey = "userId=" + null + "&loanId=" + contractReqParam.getLoanId() + "&contractType=" + viewLoanContractType.getContractType();
                redisService.put(Tools.cryptMd5(contractKey), viewLoanContractType.getDownloadPath(), 86400);
                log.info("合同展示用户：" + contractReqParam.getLoginPhone() + "--订单loanId：" + contractReqParam.getLoanId() + " 下单后查看合同下载链接redis保存成功：" + viewLoanContractType.getDownloadPath());
                String path = initInfo.getFiledownUrl() + "?" + contractKey;
                if(!Tools.isEmpty(viewLoanContractType.getDownloadPath())){
                    String postfix = viewLoanContractType.getDownloadPath().split("[.]")[1];
                    redisService.put(contractReqParam.getLoanId() + viewLoanContractType.getContractType(), path + ";" + viewLoanContractType.getContractName() + "." + postfix, ContantsSys.REDIS_TIMEOUT);
                }
                orModel.setType(viewLoanContractType.getContractType());

            }
            contractList.add(orModel);
        }
        return ResultUtil.success(contractList);
    }


    @ApiOperation(value = "下载合同", notes = "")
    @RequestMapping(value = {"/downContract"}, method = RequestMethod.GET)
    public ReplyBody downContract(@RequestParam String loginPhone, String token, String reqTime, String appId, String type, String loanId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("下载合同请求手机号(" + loginPhone + "),请求时间：" + reqTime);
        ReplyBody replyBody = vaildAuthService.vaildAuth(token);
        if (0 != replyBody.getCode()) {
            return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
        }

        String downLoadPath = redisService.get(loanId + type);

        if (!Tools.isEmpty(downLoadPath)) {
            String[] pathArr = downLoadPath.split("[;]");
            log.info("下载合同,取出链接地址：" + pathArr[0]);
            try {
                request.setCharacterEncoding("UTF-8");

                BufferedInputStream bis = null;
                BufferedOutputStream bos = null;
                String filPath = pathArr[0];

                response.setContentType("application/octet-stream");
                response.reset();//清除response中的缓存
                //根据网络文件地址创建URL
                URL url = new URL(filPath);
                //获取此路径的连接
                URLConnection conn = url.openConnection();

                Long fileLength = conn.getContentLengthLong();//获取文件大小
                //设置reponse响应头，真实文件名重命名，就是在这里设置，设置编码
                response.setHeader("Content-disposition",
                        "attachment; filename=" + new String(pathArr[1].getBytes("utf-8"), "ISO8859-1"));
                response.setHeader("Content-Length", String.valueOf(fileLength));

                bis = new BufferedInputStream(conn.getInputStream());//构造读取流
                bos = new BufferedOutputStream(response.getOutputStream());//构造输出流
                byte[] buff = new byte[1024];
                int bytesRead;
                //每次读取缓存大小的流，写到输出流
                while (-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
                    bos.write(buff, 0, bytesRead);
                }
                response.flushBuffer();//将所有的读取的流返回给客户端
                bis.close();
                bos.close();
                log.info("下载成功：" + loginPhone);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            return ResultUtil.success();

        } else {
            return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
        }


    }


    @ApiOperation(value = "查询自己权限下的放款总览", notes = "")
    @RequestMapping(value = {"/queryGrantReview"}, method = RequestMethod.POST)
    public ReplyBody queryGrantReview(@RequestBody GrantReviewParam grantReviewParam, BindingResult bindingResult) {
        log.info("查询自己权限下的放款总览,请求参数" + Tools.sendJson(grantReviewParam));
        if (bindingResult.hasErrors()) {
            return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
        }
        if (Tools.isEmpty(grantReviewParam.getAuthId())) {
            return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
        }
        ReplyBody replyBody = vaildAuthService.vaildAuth(grantReviewParam.getToken());
        if (0 != replyBody.getCode()) {
            return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
        }
        LGrantStatusQueryReqDto lGrantStatusQueryReqDto = new LGrantStatusQueryReqDto();

        List<String> businessOrgIds = new ArrayList<String>();
        String pid = redisService.get(grantReviewParam.getLoginPhone() + "pid");
        Result<List<PManageDTO>> pManageByParams = personnelManageFacadeService.getPManageByParams(null, Long.valueOf(pid), null, null, PlatformIdEnum.ORG_CRM_MERCHANT.getIndex());
        log.info("根据pid(" + pid + ")查询机构信息，后台返回：" + Tools.sendJson(pManageByParams));
        if (ResultEnum.SUCCESS.getCode()== pManageByParams.getCode()) {
            //List<PManageDTO> pManageDTOList = JSON.parseArray(pmByPrentPid.getData(), PManageDTO.class);
            if (!Tools.isEmpty(pManageByParams.getData()) && pManageByParams.getData().size() > 0) {

                for (PManageDTO pManageDTO : pManageByParams.getData()) {
                    businessOrgIds.add(pManageDTO.getOrgCode());
                }
                lGrantStatusQueryReqDto.setBusinessOrgIds(businessOrgIds);

                AuthReqParam authReqParam = new AuthReqParam();
                authReqParam.setAuthId(grantReviewParam.getAuthId());
                authReqParam.setLoginPhone(grantReviewParam.getLoginPhone());
                List<Map<String, AuthInfo>> orderStatusArray = authService.queryMenuStrByAuthId(authReqParam);
                StringBuilder beforeString = new StringBuilder();
                Map<String, Integer> respResult = new HashMap<String, Integer>();
                Map<String, String> uniqueCodeResult = new HashMap<String, String>();
                if (orderStatusArray.size() > 0) {
                    for (Map<String, AuthInfo> map : orderStatusArray) {
                        for (String k : map.keySet()) {
                            beforeString.append(k).append(",");
                            respResult.put(k, map.get(k).getId());
                            uniqueCodeResult.put(k, map.get(k).getUniqueCode());
                        }
                    }
                    String grantStatusStr = beforeString.toString().substring(0, beforeString.toString().length() - 1);

                    lGrantStatusQueryReqDto.setGrantTypeArray(grantStatusStr);
                    log.info("查询自己权限下的放款总览,请求参数：" + Tools.sendJson(lGrantStatusQueryReqDto));
                    LGrantStatusQueryRespDto lGrantStatusQueryRespDto = lOrderManager.queryGrantReview(lGrantStatusQueryReqDto);
                    log.info("查询自己权限下的放款总览,后台返回结果：" + Tools.sendJson(lGrantStatusQueryRespDto));
                    if ("000".equals(lGrantStatusQueryRespDto.getRespCode())) {
                        List<LGrantStatusQueryRespDetail> lOrderStatusQueryRespDetailDtoListBeanList = JSON.parseArray(JSON.toJSON(lGrantStatusQueryRespDto.getlGrantStatusQueryRespDetailDtoList()).toString(), LGrantStatusQueryRespDetail.class);
                        List<LGrantStatusQueryRespDetail> respList = new ArrayList<LGrantStatusQueryRespDetail>();
                        for (LGrantStatusQueryRespDetail lGrantStatusQueryRespDetail : lOrderStatusQueryRespDetailDtoListBeanList) {
                            lGrantStatusQueryRespDetail.setUniqueCode(uniqueCodeResult.get(lGrantStatusQueryRespDetail.getGrantType()));
                            Integer grantType = respResult.get(lGrantStatusQueryRespDetail.getGrantType());
                            lGrantStatusQueryRespDetail.setGrantType(String.valueOf(grantType));
                            respList.add(lGrantStatusQueryRespDetail);
                        }

                        return ResultUtil.success(respList);
                    } else {
                        return ResultUtil.error(Integer.valueOf(lGrantStatusQueryRespDto.getRespCode()), lGrantStatusQueryRespDto.getRespMesg());
                    }
                } else {
                    return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
                }

            } else {
                log.info("根据pid(" + pid + ")查询机构信息为空");
                return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
            }
        }else{
            return ResultUtil.error(Integer.valueOf(pManageByParams.getCode()), pManageByParams.getMessage());
        }
    }


        @ApiOperation(value = "根据筛选条件查询指定范围订单", notes = "")
        @RequestMapping(value = {"/queryOrderByPage"}, method = RequestMethod.POST)
        public ReplyBody queryOrderByPage (@Valid @RequestBody LOrderQueryReqParam lOrderQueryReqParam, BindingResult bindingResult){
            log.info("根据筛选条件查询指定范围订单,请求参数：" + Tools.sendJson(lOrderQueryReqParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody replyBody = vaildAuthService.vaildAuth(lOrderQueryReqParam.getToken());
            if (0 != replyBody.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            LOrderQueryReqDto lOrderQueryReqDto = new LOrderQueryReqDto();
            if (!Tools.isEmpty(lOrderQueryReqParam.getBusinessOrgId())) {
                lOrderQueryReqDto.setBusinessOrgId(lOrderQueryReqParam.getBusinessOrgId());
            } else {
                StringBuilder sBuilder = new StringBuilder();
                String pid = redisService.get(lOrderQueryReqParam.getLoginPhone() + "pid");

                Result<List<PManageDTO>> pManageByParams = personnelManageFacadeService.getPManageByParams(null, Long.valueOf(pid), null, null, PlatformIdEnum.ORG_CRM_MERCHANT.getIndex());
                log.info("根据pid(" + pid + ")查询机构信息，后台返回：" + Tools.sendJson(pManageByParams));
                if (ResultEnum.SUCCESS.getCode()== pManageByParams.getCode()) {
                    //List<PManageDTO> pManageDTOList = JSON.parseArray(pmByPrentPid.getData(), PManageDTO.class);
                    if (!Tools.isEmpty(pManageByParams.getData()) && pManageByParams.getData().size() > 0) {

                        for (PManageDTO pManageDTO : pManageByParams.getData()) {
                            sBuilder.append(pManageDTO.getOrgCode()).append(",");
                        }
                        String businessOrgIds = sBuilder.toString().substring(0, sBuilder.toString().length() - 1);
                        lOrderQueryReqDto.setBusinessOrgId(businessOrgIds);
                    }else{
                        return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(),RespCode.NO_AUTH_VISIT.getMsg());
                    }
                } else {
                    return ResultUtil.error(Integer.valueOf(pManageByParams.getCode()), pManageByParams.getMessage());
                }

            }

            String examineStatus = null;
            if (lOrderQueryReqParam.getSearchType().equals(SearchType.NAME.name())
                    || lOrderQueryReqParam.getSearchType().equals(SearchType.PHONE.name())
                    || lOrderQueryReqParam.getSearchType().equals(SearchType.LOAN_CODE.name())
                    || lOrderQueryReqParam.getSearchType().equals(SearchType.IDCARD.name())) {
                examineStatus = lOrderQueryReqParam.getSearchType();
            } else {
                AuthReqParam authReqParam = new AuthReqParam();
                authReqParam.setLoginPhone(lOrderQueryReqParam.getLoginPhone());
                authReqParam.setAuthId(lOrderQueryReqParam.getAuthId());
                authReqParam.setId(lOrderQueryReqParam.getId());
                Map<String, Integer> existAuthMap = authService.queryMenuExistByAuthId(authReqParam);
                if (existAuthMap.size() == 0 || Tools.isEmpty(existAuthMap)) {
                    return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
                }
                for (String k : existAuthMap.keySet()) {
                    examineStatus = k;
                }
            }


            if (Tools.isEmpty(lOrderQueryReqParam.getSearchType())) {
                if (!Tools.isEmpty(lOrderQueryReqParam.getCommodityId())) {
                    lOrderQueryReqDto.setCommodityId(lOrderQueryReqParam.getCommodityId());
                }
                if (!Tools.isEmpty(lOrderQueryReqParam.getStartDate())) {
                    lOrderQueryReqDto.setStartDate(Tools.strToDate(lOrderQueryReqParam.getStartDate()));
                }
                if (!Tools.isEmpty(lOrderQueryReqParam.getEndDate())) {
                    lOrderQueryReqDto.setEndDate(Tools.strToDate(lOrderQueryReqParam.getEndDate()));
                }
                if ("2".equals(lOrderQueryReqParam.getFrom())) {
                    if (examineStatus.equals(OrderEnum.YESTERDAY_LOAN.getName())) {
                        lOrderQueryReqDto.setExamineStatus(SearchType.YESTERDAY_GRANT.name());
                    } else if (examineStatus.equals(OrderEnum.THE_LAST_THREE_DAYS.getName())) {
                        lOrderQueryReqDto.setExamineStatus(SearchType.THREE_GRANTINFO.name());
                    } else if (examineStatus.equals(OrderEnum.MONTH_LOAN.getName())) {
                        lOrderQueryReqDto.setExamineStatus(SearchType.THIS_MONTH.name());
                    } else if (examineStatus.equals(OrderEnum.LAST_MONTH_LOAN.getName())) {
                        lOrderQueryReqDto.setExamineStatus(SearchType.LAST_MONTH.name());
                    } else if (examineStatus.equals(OrderEnum.LOAN_YEAR.getName())) {
                        lOrderQueryReqDto.setExamineStatus(SearchType.THIS_YEAR.name());
                    } else {
                        return ResultUtil.error(RespCode.SEARCH_NO_HAS.getCode(), RespCode.SEARCH_NO_HAS.getMsg());
                    }
                } else {
                    lOrderQueryReqDto.setExamineStatus(examineStatus);
                }

            } else {
                if (examineStatus.equals(OrderEnum.TEMPLATE_1.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.TODAY_CREATE.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_2.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.YESTERDAY_CREATE.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_3.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.YESTERDAY_GRANT.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_8.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.THREE_GRANTINFO.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_9.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.THREE_GRANTPAY.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_10.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.THREE_CANCEL.name());
                } else if (examineStatus.equals(OrderEnum.TEMPLATE_11.getName())) {
                    lOrderQueryReqDto.setExamineStatus(SearchType.THREE_REFUSE.name());
                } else {
                    lOrderQueryReqDto.setExamineStatus(lOrderQueryReqParam.getSearchType());
                }

                Map<String, Object> map = new HashMap<String, Object>();
                map.put("condition", lOrderQueryReqParam.getSearchValue());
                lOrderQueryReqDto.setConditionMap(map);
            }
            lOrderQueryReqDto.setLimit(lOrderQueryReqParam.getLimit());
            lOrderQueryReqDto.setStartPage(lOrderQueryReqParam.getStartPage());
            log.info("根据筛选条件查询指定范围订单(" + lOrderQueryReqParam.getLoginPhone() + "),请求参数：" + Tools.sendJson(lOrderQueryReqDto));
            PageObject pageObject = lOrderManager.queryOrderByPage(lOrderQueryReqDto);
            log.info("根据筛选条件查询指定范围订单(" + lOrderQueryReqParam.getLoginPhone() + "),返回结果：" + Tools.sendJson(pageObject));
            if (!"000".equals(pageObject.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }
            List<LOrderQueryResultDto> lOrderQueryResultDtoList = pageObject.getResults();
            List<LOrderQueryResult> lOrderQueryResultVOlist = new ArrayList<LOrderQueryResult>();
            for (LOrderQueryResultDto lOrderQueryResultDto : lOrderQueryResultDtoList) {
                lOrderQueryResultVOlist.add(new LOrderQueryResult(lOrderQueryResultDto, lOrderQueryReqParam.getFrom(), examineStatus));
            }
            pageObject.setResults(lOrderQueryResultVOlist);
            return ResultUtil.success(pageObject);
        }


        @ApiOperation(value = "根据订单ID查询订单进度", notes = "")
        @PostMapping(value = {"/queryOrderProgress"})
        public ReplyBody queryOrderProgress (@Valid @RequestBody QueryOrderReqParam orderProgress, BindingResult
        bindingResult){
            log.info("根据订单ID查询订单进度,请求参数：" + Tools.sendJson(orderProgress));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody replyBody = vaildAuthService.vaildAuth(orderProgress.getToken());
            if (0 != replyBody.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            LOrderProgressDto lOrderProgressDto = lOrderManager.queryOrderProgress(orderProgress.getLoanId());
            log.info("根据订单ID(" + orderProgress.getLoanId() + ")查询订单进度,返回结果：" + Tools.sendJson(lOrderProgressDto));
            if (!"000".equals(lOrderProgressDto.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), lOrderProgressDto.getRespMesg());
            }

            LOrderProgress lOrderProgress = new LOrderProgress(personnelManageFacadeService, redisService, lOrderProgressDto);


            return ResultUtil.success(lOrderProgress);
        }


        @ApiOperation(value = "根据订单ID查询订单详情", notes = "")
        @PostMapping(value = {"/getOrderDetailById"})
        public ReplyBody getOrderDetailById (@Valid @RequestBody OrderDetailReqParam orderDetailReqParam, BindingResult
        bindingResult){
            log.info("根据订单ID查询订单详情,请求参数：" + Tools.sendJson(orderDetailReqParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody replyBody = vaildAuthService.vaildAuth(orderDetailReqParam.getToken());
            if (0 != replyBody.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            LOrderDetailDto orderDetailById = lOrderManager.getOrderDetailById(orderDetailReqParam.getLoanId());
            log.info("根据订单ID(" + orderDetailReqParam.getLoanId() + ")查询订单详情,后台返回：" + Tools.sendJson(orderDetailReqParam));
            if (!"000".equals(orderDetailById.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }
            LOrderDetail lOrderDetail = new LOrderDetail();
            lOrderDetail.setLoanCode(orderDetailById.getLoanCode());
            lOrderDetail.setOrgShortName(orderDetailById.getOrgShortName());
            lOrderDetail.setCommodityName(orderDetailById.getCommodityName());

            String planName = orderDetailById.getPlan();
            if (!Tools.isEmpty(planName)) {
                String subPlanName = null;
                if (planName.contains("(")) {
                    subPlanName = planName.substring(0, planName.indexOf("("));
                } else if (planName.contains("（")) {
                    subPlanName = planName.substring(0, planName.indexOf("（"));
                } else {
                    subPlanName = planName;
                }
                lOrderDetail.setPlanName(subPlanName);
            } else {
                lOrderDetail.setPlanName(planName);
            }
            lOrderDetail.setLoanMoney(orderDetailById.getLoanMoney());
            lOrderDetail.setBusinessOrgBankNum(orderDetailById.getBusinessOrgBankNum());
            if (!Tools.isEmpty(orderDetailById.getCreateDate())) {
                lOrderDetail.setCreateDate(Tools.formatyyyyMMddhhmmss(orderDetailById.getCreateDate()));
            }
            if (!Tools.isEmpty(orderDetailById.getExamineDate())) {
                lOrderDetail.setExamineDate(Tools.formatyyyyMMddhhmmss(orderDetailById.getExamineDate()));
            }
            if (!Tools.isEmpty(orderDetailById.getGrantInfoDate())) {
                lOrderDetail.setGrantInfoDate(Tools.formatyyyyMMddhhmmss(orderDetailById.getGrantInfoDate()));
            }
            if (!Tools.isEmpty(orderDetailById.getGrantInfoDate())) {
                lOrderDetail.setClearingDate(Tools.formatyyyyMMddhhmmss(orderDetailById.getClearingDate()));
            }


            return ResultUtil.success(lOrderDetail);
        }

        @ApiOperation(value = "根据订单ID取消订单", notes = "")
        @PostMapping(value = {"/cancelLoan"})
        public ReplyBody cancelLoan (@Valid @RequestBody OrderDetailReqParam orderDetailReqParam, BindingResult
        bindingResult){
            log.info("根据订单ID取消订单,请求参数：" + Tools.sendJson(orderDetailReqParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(orderDetailReqParam.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            String[] loanIDSplit = orderDetailReqParam.getLoanId().split("\\,");
            List<String> idList = new ArrayList<String>();
            for (String id : loanIDSplit) {
                idList.add(id);
            }
            BaseSerializable baseSerializable = lOrderManager.cancelLoan(idList);
            log.info("根据订单ID取消订单,后台返回：" + Tools.sendJson(baseSerializable));
            ReplyBody replyBody = new ReplyBody();
            if ("000".equals(baseSerializable.getRespCode())) {
//            return ResultUtil.success(baseSerializable);
                return ResultUtil.success();
            } else {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }

        }


        @ApiOperation(value = "根据订单ID查询还款计划", notes = "")
        @PostMapping(value = {"/queryRmpbyLoanId"})
        public ReplyBody queryRmpbyLoanId (@Valid @RequestBody OrderDetailReqParam orderDetailReqParam, BindingResult
        bindingResult){
            log.info("根据订单ID查询还款计划,请求参数：" + Tools.sendJson(orderDetailReqParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(orderDetailReqParam.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            LRmpDetailDto lRmpDetailDto = lOrderManager.queryRmpbyLoanId(orderDetailReqParam.getLoanId());
            log.info("根据订单ID查询还款计划,返回结果：" + Tools.sendJson(lRmpDetailDto));
            if (!"000".equals(lRmpDetailDto.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), lRmpDetailDto.getRespMesg());
            }
            LRmpDetail lRmpDetail = new LRmpDetail(lRmpDetailDto.getlRmpDetailDtoList());
            return ResultUtil.success(lRmpDetail);
        }


        @ApiOperation(value = "立即解挂", notes = "")
        @PostMapping(value = {"/dealHangUp"})
        public ReplyBody dealHangUp (@Valid @RequestBody DealHangUpReqParam dealHangUpReqParam, BindingResult
        bindingResult){
            log.info("立即解挂,请求参数：" + Tools.sendJson(dealHangUpReqParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(dealHangUpReqParam.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            HangupRequ hangupRequ = new HangupRequ();
            hangupRequ.setDealUser(redisService.get(dealHangUpReqParam.getLoginPhone() + "realName"));
            hangupRequ.setLoanId(dealHangUpReqParam.getLoanId());
            if ("01".equals(dealHangUpReqParam.getHangType())) {
                List<ContactUser> userList = new ArrayList<ContactUser>();
                for (ContactsInfo contactsInfo : dealHangUpReqParam.getContactsInfoList()) {
                    ContactUser contactUser = new ContactUser();
                    contactUser.setUserName(contactsInfo.getName());
                    contactUser.setPhone(contactsInfo.getPhone());
                    contactUser.setType(contactsInfo.getRelationshipType());
                    userList.add(contactUser);
                }
                hangupRequ.setContactUserList(userList);
            }
            hangupRequ.setCode(dealHangUpReqParam.getHangType());
            HangUpResp hangUpResp = auditHangupService.dealHangUp(hangupRequ);
            log.info("立即解挂,后台返回结果：" + Tools.sendJson(hangUpResp));
            if (!"01".equals(hangUpResp.getCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), hangUpResp.getMsg());
            }
            return ResultUtil.success();
        }


        @ApiOperation(value = "查询挂起运营商状态", notes = "")
        @PostMapping(value = {"/queryHangupInfos"})
        public ReplyBody queryHangupInfos (@Valid @RequestBody QueryHangupInfo queryHangupInfo, BindingResult
        bindingResult){
            log.info("查询运营商状态,请求参数：" + Tools.sendJson(queryHangupInfo));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(queryHangupInfo.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            String personPhone = redisService.get(queryHangupInfo.getLoanId() + "personPhone");
            HangupInfoResp hangupInfoResp = auditHangupService.queryHangupInfos(queryHangupInfo.getLoanId(), personPhone);
            log.info("查询运营商状态,后台返回结果：" + Tools.sendJson(hangupInfoResp));
            if (!"01".equals(hangupInfoResp.getCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), hangupInfoResp.getMsg());
            }
            return ResultUtil.success(hangupInfoResp.getHangUpInfoList());
        }


        @ApiOperation(value = "确认放款", notes = "")
        @PostMapping(value = {"/confirmGrantInfo"})
        public ReplyBody confirmGrantInfo (@Valid @RequestBody ConfirmGrantInfo confirmGrantInfo, BindingResult
        bindingResult){
            log.info("确认放款,请求参数：" + Tools.sendJson(confirmGrantInfo));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(confirmGrantInfo.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
            String pid = redisService.get(confirmGrantInfo.getLoginPhone() + "pid");
            String role_id = redisService.get(confirmGrantInfo.getLoginPhone() + "role_id");
            params.add(new BasicNameValuePair("pid", pid));
            params.add(new BasicNameValuePair("flatform_type", PlatformIdEnum.ORG_CRM_MERCHANT.getIndex()));
            params.add(new BasicNameValuePair("role_id", role_id));
            String parentResourcesUrl = initInfo.getParentreSourcesUrl();
            String parentResourcesResult = HttpTools.urlPost(parentResourcesUrl, params);

            Resources parentResources = JSON.parseObject(parentResourcesResult, Resources.class);
            if (1 != parentResources.getState()) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }
            List<ResourcesBean> parentResourcesBeanList = JSON.parseArray(parentResources.getJson(), ResourcesBean.class);
            int x = 0;
            for (ResourcesBean resourcesBean : parentResourcesBeanList) {
                if (confirmGrantInfo.getId().equals(String.valueOf(resourcesBean.getId()))) {
                    x++;
                    break;
                }
            }

            if (x == 0) {
                return ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(), RespCode.NO_AUTH_VISIT.getMsg());
            }

            String[] loanIDSplit = confirmGrantInfo.getLoanId().split("\\,");
            List<String> loanIds = new ArrayList<String>();
            for (String loanID : loanIDSplit) {
                loanIds.add(loanID);
            }
            BaseSerializable baseSerializable = lOrderManager.confirmGrantInfo(loanIds, redisService.get(confirmGrantInfo.getLoginPhone() + "realName"));
            log.info("确认放款,后台返回结果：" + Tools.sendJson(baseSerializable));
            if (!"000".equals(baseSerializable.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }
            return ResultUtil.success();
        }

        @ApiOperation(value = "查询极简模版列表", notes = "")
        @PostMapping(value = {"/templateList"})
        public ReplyBody templateList (@Valid @RequestBody TemplateParam templateParam, BindingResult bindingResult){
            log.info("查询极简模版列表,请求参数：" + Tools.sendJson(templateParam));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(templateParam.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            AuthReqParam authReqParam = new AuthReqParam();
            authReqParam.setAuthId(templateParam.getAuthId());
            authReqParam.setLoginPhone(templateParam.getLoginPhone());
            ReplyBody<AuthRespParam> authRespParamReplyBody = authService.queryMenuByAuthId(authReqParam);
            return authRespParamReplyBody;
        }

        @ApiOperation(value = "搜索-极简模式", notes = "")
        @PostMapping(value = {"/queryOrder"})
        public ReplyBody queryOrder (@Valid @RequestBody QueryOrder queryOrder, BindingResult bindingResult){
            log.info("搜索-极简模式,请求参数：" + Tools.sendJson(queryOrder));
            if (bindingResult.hasErrors()) {
                return ResultUtil.error(RespCode.RESP_CODE_PARMISNULL.getCode(), bindingResult.getFieldError().getDefaultMessage());
            }
            ReplyBody vaildTokenResult = vaildAuthService.vaildAuth(queryOrder.getToken());
            if (0 != vaildTokenResult.getCode()) {
                return ResultUtil.error(RespCode.NO_LOGIN_TIMEOUT.getCode(), RespCode.NO_LOGIN_TIMEOUT.getMsg());
            }
            LOrderSearchReqDto lOrderSearchReqDto = new LOrderSearchReqDto();

            StringBuilder sBuilder = new StringBuilder();
            String pid = redisService.get(queryOrder.getLoginPhone() + "pid");
            Result<List<PManageDTO>> pManageByParams = personnelManageFacadeService.getPManageByParams(null, Long.valueOf(pid), null, null, PlatformIdEnum.ORG_CRM_MERCHANT.getIndex());
            log.info("根据pid(" + pid + ")查询机构信息，后台返回：" + Tools.sendJson(pManageByParams));
            if (ResultEnum.SUCCESS.getCode()== pManageByParams.getCode()) {
                //List<PManageDTO> pManageDTOList = JSON.parseArray(pmByPrentPid.getData(), PManageDTO.class);
                if (!Tools.isEmpty(pManageByParams.getData()) && pManageByParams.getData().size() > 0) {

                    for (PManageDTO pManageDTO : pManageByParams.getData()) {
                        sBuilder.append(pManageDTO.getOrgCode()).append(",");
                    }
                    String businessOrgIds = sBuilder.toString().substring(0, sBuilder.toString().length() - 1);
                    lOrderSearchReqDto.setBusinessOrgIds(businessOrgIds);
                }else{
                    ResultUtil.error(RespCode.NO_AUTH_VISIT.getCode(),RespCode.NO_AUTH_VISIT.getMsg());
                }
            } else {
                return ResultUtil.error(Integer.valueOf(pManageByParams.getCode()), pManageByParams.getMessage());
            }

            if (queryOrder.getSearchType().equals(SearchType.NAME.name())) {
                lOrderSearchReqDto.setSearchType(SearchType.NAME);
                lOrderSearchReqDto.setSearchValue(queryOrder.getSearchValue());
            } else if (queryOrder.getSearchType().equals(SearchType.PHONE.name())) {
                lOrderSearchReqDto.setSearchType(SearchType.PHONE);
                lOrderSearchReqDto.setSearchValue(queryOrder.getSearchValue());
            } else if (queryOrder.getSearchType().equals(SearchType.LOAN_CODE.name())) {
                lOrderSearchReqDto.setSearchType(SearchType.LOAN_CODE);
                lOrderSearchReqDto.setSearchValue(queryOrder.getSearchValue());
            } else if (queryOrder.getSearchType().equals(SearchType.IDCARD.name())) {
                lOrderSearchReqDto.setSearchType(SearchType.IDCARD);
                lOrderSearchReqDto.setSearchValue(queryOrder.getSearchValue());
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_1.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.TODAY_CREATE);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_2.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.YESTERDAY_CREATE);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_3.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.YESTERDAY_GRANT);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_8.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.THREE_GRANTINFO);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_9.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.THREE_GRANTPAY);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_10.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.THREE_CANCEL);
            } else if (queryOrder.getSearchType().equals(OrderEnum.TEMPLATE_11.getName())) {
                lOrderSearchReqDto.setSearchType(SearchType.THREE_REFUSE);
            } else {
                return ResultUtil.error(RespCode.SEARCH_NO_HAS.getCode(), RespCode.SEARCH_NO_HAS.getMsg());
            }


            LOrderStatusQueryRespDetailDto lOrderStatusQueryRespDetailDto = lOrderManager.queryOrder(lOrderSearchReqDto);
            log.info("搜索-极简模式,后台返回结果：" + Tools.sendJson(lOrderStatusQueryRespDetailDto));
            if (!"000".equals(lOrderStatusQueryRespDetailDto.getRespCode())) {
                return ResultUtil.error(RespCode.RESP_CODE_ERRO.getCode(), RespCode.RESP_CODE_ERRO.getMsg());
            }


            return ResultUtil.success(lOrderStatusQueryRespDetailDto);
        }

    }
