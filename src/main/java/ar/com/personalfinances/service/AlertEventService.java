package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.EntityEvent;

public interface AlertEventService {

    void saveAccountAlert(EntityEvent event, long entityId, String details, long userId);

    void saveCategoryAlert(EntityEvent event, long entityId, String details, long userId);

    void saveExpenseAlert(EntityEvent event, long entityId, String details, long userId);

    void saveExpensesGroupAlert(EntityEvent event, long entityId, String details, long userId);

    void saveSharedExpenseAlert(EntityEvent event, long entityId, String details, long userId);

    void saveUserAlert(EntityEvent event, long entityId, String details, long userId);
}