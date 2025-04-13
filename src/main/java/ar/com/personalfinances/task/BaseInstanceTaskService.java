package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.util.CommonResult;

public abstract class BaseInstanceTaskService {

    public abstract CommonResult runTask(InstanceTask instanceTask);
}