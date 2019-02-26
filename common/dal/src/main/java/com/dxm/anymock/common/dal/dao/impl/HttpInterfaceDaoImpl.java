package com.dxm.anymock.common.dal.dao.impl;

import com.dxm.anymock.common.base.GlobalConstant;
import com.dxm.anymock.common.base.entity.*;
import com.dxm.anymock.common.base.utils.ConvertUtils;
import com.dxm.anymock.common.base.enums.ErrorCode;
import com.dxm.anymock.common.base.exception.BaseException;
import com.dxm.anymock.common.dal.dao.*;
import com.dxm.anymock.common.dal.dao.httpinterface.BranchScriptDao;
import com.dxm.anymock.common.dal.dao.httpinterface.CallbackRequestHeaderDao;
import com.dxm.anymock.common.dal.dao.httpinterface.ResponseHeaderDao;
import com.dxm.anymock.common.dal.entity.*;
import com.dxm.anymock.common.dal.mapper.auto.*;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
@Transactional
public class HttpInterfaceDaoImpl implements HttpInterfaceDao {

    private static final Logger logger = LoggerFactory.getLogger(HttpInterfaceDaoImpl.class);

    @Autowired
    private HttpInterfacePOMapper httpInterfacePOMapper;

    @Autowired
    private ResponseHeaderDao responseHeaderDao;

    @Autowired
    private CallbackRequestHeaderDao callbackRequestHeaderDao;

    @Autowired
    private BranchScriptDao branchScriptDao;

    @Override
    public List<HttpInterface> selectAll() {
        HttpInterfacePOExample example = new HttpInterfacePOExample();
        // example.createCriteria().andRequestUriLike

        // example.createCriteria()
        return ConvertUtils.convert(httpInterfacePOMapper.selectByExample(example), HttpInterface.class);
    }

    @Override
    public List<HttpInterface> selectBySpaceId(Long subSpaceId) {
        HttpInterfacePOExample example = new HttpInterfacePOExample();
        example.createCriteria().andSpaceIdEqualTo(subSpaceId);
        return ConvertUtils.convert(httpInterfacePOMapper.selectByExample(example), HttpInterface.class);
    }

    @Override
    public HttpInterface selectByRequestType(RequestType requestType) {
        HttpInterfacePOExample httpInterfacePOExample = new HttpInterfacePOExample();
        httpInterfacePOExample.createCriteria()
                .andRequestMethodEqualTo(requestType.getMethod())
                .andRequestUriEqualTo(requestType.getUri());
        List<HttpInterfacePOWithBLOBs> httpInterfacePOWithBLOBsList
                = httpInterfacePOMapper.selectByExampleWithBLOBs(httpInterfacePOExample);
        if (httpInterfacePOWithBLOBsList.size() == 0) {
            return null;
        } else if (httpInterfacePOWithBLOBsList.size() == 1) {
            HttpInterface httpInterface
                    = ConvertUtils.convert(httpInterfacePOWithBLOBsList.get(0), HttpInterface.class);
            fillingSubProperties(httpInterface, false);
            return httpInterface;
        } else {
            throw new IncorrectResultSizeDataAccessException(1, httpInterfacePOWithBLOBsList.size());
        }
    }

    @Override
    public BranchScript selectBranchScript(Long id, String branchName) {
        return branchScriptDao.selectByHttpInterfaceIdAndName(id, branchName);
    }

    @Override
    public HttpInterface selectById(Long id) {
        HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs = httpInterfacePOMapper.selectByPrimaryKey(id);
        if (httpInterfacePOWithBLOBs == null) {
            throw new BaseException(ErrorCode.HTTP_INTERFACE_NOT_FOUND);
        }
        HttpInterface httpInterface = ConvertUtils.convert(httpInterfacePOWithBLOBs, HttpInterface.class);
        fillingSubProperties(httpInterface, true);
        return httpInterface;
    }

    @Override
    public void insert(HttpInterface httpInterface) {
        HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs
                = ConvertUtils.convert(httpInterface, HttpInterfacePOWithBLOBs.class);
        fillingLastUpdateInfo(httpInterfacePOWithBLOBs);
        int resultSize;
        try {
            resultSize = httpInterfacePOMapper.insert(httpInterfacePOWithBLOBs);
        } catch (DuplicateKeyException e) {
            throw new BaseException(ErrorCode.HTTP_INTERFACE_DUPLICATE_KEY);
        }

        if (resultSize != 1) {
            throw new IncorrectResultSizeDataAccessException(1, resultSize);
        }

        // get httpInterface.id
        HttpInterfacePOExample httpInterfacePOExample = new HttpInterfacePOExample();
        httpInterfacePOExample.createCriteria()
                .andRequestMethodEqualTo(httpInterface.getRequestMethod())
                .andRequestUriEqualTo(httpInterface.getRequestUri());
        List<HttpInterfacePOWithBLOBs> httpInterfacePOWithBLOBsList
                = httpInterfacePOMapper.selectByExampleWithBLOBs(httpInterfacePOExample);
        if (httpInterfacePOWithBLOBsList.size() != 1) {
            throw new IncorrectResultSizeDataAccessException(1, httpInterfacePOWithBLOBsList.size());
        }

        Long httpInterfaceId = httpInterfacePOWithBLOBsList.get(0).getId();
        responseHeaderDao.insert(httpInterfaceId, httpInterface.getResponseHeaderList());
        callbackRequestHeaderDao.insert(httpInterfaceId, httpInterface.getCallbackRequestHeaderList());
        branchScriptDao.insert(httpInterfaceId, httpInterface.getBranchScriptList());
    }

    @Override
    public void update(HttpInterface httpInterface) {
        // update httpInterface
        HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs
                = ConvertUtils.convert(httpInterface, HttpInterfacePOWithBLOBs.class);
        fillingLastUpdateInfo(httpInterfacePOWithBLOBs);
        int resultSize;
        try {
            resultSize = httpInterfacePOMapper.updateByPrimaryKeyWithBLOBs(httpInterfacePOWithBLOBs);
        } catch (DuplicateKeyException e) {
            throw new BaseException(ErrorCode.HTTP_INTERFACE_DUPLICATE_KEY);
        }

        if (resultSize == 0) {
            throw new BaseException(ErrorCode.HTTP_INTERFACE_NOT_FOUND);
        } else if (resultSize != 1) {
            throw new IncorrectResultSizeDataAccessException(1, resultSize);
        }

        Long httpInterfaceId = httpInterface.getId();
        // todo 先diff一次，如果完全相当则不用执行相关逻辑
        responseHeaderDao.deleteByHttpInterfaceId(httpInterfaceId);
        responseHeaderDao.insert(httpInterfaceId, httpInterface.getResponseHeaderList());

        // todo 先diff一次，如果完全相当则不用执行相关逻辑
        callbackRequestHeaderDao.deleteByHttpInterfaceId(httpInterfaceId);
        callbackRequestHeaderDao.insert(httpInterfaceId, httpInterface.getCallbackRequestHeaderList());

        // todo 先diff一次，如果完全相当则不用执行相关逻辑
        branchScriptDao.deleteByHttpInterfaceId(httpInterfaceId);
        branchScriptDao.insert(httpInterfaceId, httpInterface.getBranchScriptList());
    }

    @Override
    public void delete(Long id) {
        responseHeaderDao.deleteByHttpInterfaceId(id);
        callbackRequestHeaderDao.deleteByHttpInterfaceId(id);
        branchScriptDao.deleteByHttpInterfaceId(id);

        int resultSize = httpInterfacePOMapper.deleteByPrimaryKey(id);
        if (resultSize == 0) {
            throw new BaseException(ErrorCode.HTTP_INTERFACE_NOT_FOUND);
        } else if (resultSize != 1) {
            throw new IncorrectResultSizeDataAccessException(1, resultSize);
        }
    }

    @Override
    public void start(Long id) {
        HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs = new HttpInterfacePOWithBLOBs();
        httpInterfacePOWithBLOBs.setId(id);
        httpInterfacePOWithBLOBs.setStart(true);
        fillingLastUpdateInfo(httpInterfacePOWithBLOBs);

        int resultSize = httpInterfacePOMapper.updateByPrimaryKeySelective(httpInterfacePOWithBLOBs);
        if (resultSize != 1) {
            throw new IncorrectResultSizeDataAccessException(1, resultSize);
        }
    }

    @Override
    public void shutdown(Long id) {
        HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs = new HttpInterfacePOWithBLOBs();
        httpInterfacePOWithBLOBs.setId(id);
        httpInterfacePOWithBLOBs.setStart(false);
        fillingLastUpdateInfo(httpInterfacePOWithBLOBs);

        int resultSize = httpInterfacePOMapper.updateByPrimaryKeySelective(httpInterfacePOWithBLOBs);
        if (resultSize != 1) {
            throw new IncorrectResultSizeDataAccessException(1, resultSize);
        }
    }

    private void fillingSubProperties(HttpInterface httpInterface, Boolean withBranchScript) {
        Long httpInterfaceId = httpInterface.getId();
        httpInterface.setResponseHeaderList(
                responseHeaderDao.selectByHttpInterfaceId(httpInterfaceId));
        httpInterface.setCallbackRequestHeaderList(
                callbackRequestHeaderDao.selectByHttpInterfaceId(httpInterfaceId));
        if (BooleanUtils.isTrue(withBranchScript)) {
            httpInterface.setBranchScriptList(
                    branchScriptDao.selectByHttpInterfaceId(httpInterfaceId));
        }
    }

    private void fillingLastUpdateInfo(HttpInterfacePOWithBLOBs httpInterfacePOWithBLOBs) {
        httpInterfacePOWithBLOBs.setLastUpdateUser(GlobalConstant.DEFAULT_USER);
        httpInterfacePOWithBLOBs.setLastUpdateTime(new Date());
    }
}