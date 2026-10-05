package com.linewell.dataelement.platform.scheduling;


import com.linewell.dataelement.model.common.CommonResponse;

public interface RunnableTask {

    CommonResponse run(Object param);
}
