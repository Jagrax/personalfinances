package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.AlertEvent;
import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.EntityType;
import ar.com.personalfinances.repository.AlertEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AlertEventServiceImpl implements AlertEventService {

    private final AlertEventRepository alertEventRepository;

    public AlertEventServiceImpl(AlertEventRepository alertEventRepository) {
        this.alertEventRepository = alertEventRepository;
    }

    @Override
    public void saveAccountAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.ACCOUNT, entityId, userId, event, details));
    }

    @Override
    public void saveCategoryAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.CATEGORY, entityId, userId, event, details));
    }

    @Override
    public void saveExpenseAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.EXPENSE, entityId, userId, event, details));
    }

    @Override
    public void saveExpensesGroupAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.EXPENSES_GROUP, entityId, userId, event, details));
    }

    @Override
    public void saveSharedExpenseAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.SHARED_EXPENSE, entityId, userId, event, details));
    }

    @Override
    public void saveUserAlert(EntityEvent event, long entityId, String details, long userId) {
        alertEventRepository.save(new AlertEvent(EntityType.USER, entityId, userId, event, details));
    }
}