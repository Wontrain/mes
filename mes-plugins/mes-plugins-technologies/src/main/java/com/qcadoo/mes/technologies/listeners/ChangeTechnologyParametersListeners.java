package com.qcadoo.mes.technologies.listeners;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.qcadoo.mes.technologies.TechnologyService;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Sets;
import com.qcadoo.mes.states.service.client.util.ViewContextHolder;
import com.qcadoo.mes.technologies.TechnologyNameAndNumberGenerator;
import com.qcadoo.mes.technologies.constants.OperationFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologyProductionLineFields;
import com.qcadoo.mes.technologies.states.TechnologyStateChangeViewClient;
import com.qcadoo.mes.technologies.states.constants.TechnologyStateStringValues;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.CheckBoxComponent;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class ChangeTechnologyParametersListeners {

    private static final String L_CHANGE_GROUP = "changeGroup";

    private static final String L_CHANGE_PERFORMANCE_NORM = "changePerformanceNorm";

    private static final String L_STANDARD_PERFORMANCE = "standardPerformance";

    private static final String L_TECHNOLOGY_GROUP = "technologyGroup";

    public static final String TIME_NEXT_OPERATION = "timeNextOperation";

    public static final String TPZ = "tpz";

    public static final String TJ = "tj";

    private static final String NEXT_OPERATION_AFTER_PRODUCED_TYPE = "nextOperationAfterProducedType";

    private static final String NEXT_OPERATION_AFTER_PRODUCED_QUANTITY = "nextOperationAfterProducedQuantity";

    private static final String PRODUCTION_IN_ONE_CYCLE = "productionInOneCycle";

    private static final String L_UPDATE_OPERATION_TIME_NORMS = "updateOperationTimeNorms";

    private static final String L_UPDATE_OPERATION_WORKSTATIONS = "updateOperationWorkstations";

    private static final Set<String> FIELDS_OPERATION = Sets.newHashSet(TPZ, TJ, PRODUCTION_IN_ONE_CYCLE,
            NEXT_OPERATION_AFTER_PRODUCED_TYPE, NEXT_OPERATION_AFTER_PRODUCED_QUANTITY, "nextOperationAfterProducedQuantityUNIT",
            TIME_NEXT_OPERATION, "machineUtilization", "laborUtilization", "productionInOneCycleUNIT",
            "areProductQuantitiesDivisible", "isTjDivisible", "minStaff", "optimalStaff", "tjDecreasesForEnlargedStaff");

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private TechnologyNameAndNumberGenerator technologyNameAndNumberGenerator;

    @Autowired
    private TechnologyStateChangeViewClient technologyStateChangeViewClient;

    @Autowired
    private TechnologyService technologyService;

    public void changeTechnologyParameters(final ViewDefinitionState view, final ComponentState state, final String[] args)
            throws JSONException {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        CheckBoxComponent generated = (CheckBoxComponent) view.getComponentByReference("generated");

        LookupComponent lookupComponent = (LookupComponent) view.getComponentByReference(L_TECHNOLOGY_GROUP);
        String code = lookupComponent.getCurrentCode();
        if (StringUtils.isNoneEmpty(code) && Objects.isNull(lookupComponent.getFieldValue())) {
            form.findFieldComponentByName(L_TECHNOLOGY_GROUP).addMessage("qcadooView.lookup.noMatchError",
                    ComponentState.MessageType.FAILURE);
            generated.setChecked(false);
            return;
        }
        Entity group = null;
        Entity entity = form.getPersistedEntityWithIncludedFormValues();

        if (entity.getBooleanField(L_CHANGE_GROUP) && Objects.nonNull(entity.getLongField(L_TECHNOLOGY_GROUP))) {
            group = dataDefinitionService
                    .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY_GROUP)
                    .get(entity.getLongField(L_TECHNOLOGY_GROUP));
            entity.setField(L_TECHNOLOGY_GROUP, group);
        }

        try {
            entity = entity.getDataDefinition().validate(entity);
            if (!entity.isValid()) {
                form.setEntity(entity);
                return;
            }
        } catch (IllegalArgumentException e) {
            form.findFieldComponentByName(L_STANDARD_PERFORMANCE)
                    .addMessage("qcadooView.validate.field.error.invalidNumericFormat", ComponentState.MessageType.FAILURE);
            generated.setChecked(false);
            return;
        }
        JSONObject context = view.getJsonContext();
        Set<Long> ids = Arrays.stream(
                        context.getString("window.mainTab.form.gridLayout.selectedEntities").replaceAll("[\\[\\]]", "").split(","))
                .map(Long::valueOf).collect(Collectors.toSet());

        BigDecimal standardPerformance = null;
        if (entity.getBooleanField(L_CHANGE_PERFORMANCE_NORM)) {
            standardPerformance = entity.getDecimalField(L_STANDARD_PERFORMANCE);
        }

        try {
            createCustomizedTechnologies(view, state, ids, entity, group, standardPerformance);
        } catch (Exception exc) {
            view.addMessage("technologies.changeTechnologyParameters.error.technologiesNotCreated",
                    ComponentState.MessageType.FAILURE);
        }
        generated.setChecked(true);
    }

    @Transactional
    private void createCustomizedTechnologies(ViewDefinitionState view, ComponentState state, Set<Long> ids, Entity entity,
                                              Entity finalGroup, BigDecimal finalStandardPerformance) {
        boolean updateOperationTimeNorms = entity.getBooleanField(L_UPDATE_OPERATION_TIME_NORMS);

        boolean updateOperationWorkstations = entity.getBooleanField(L_UPDATE_OPERATION_WORKSTATIONS);
        ids.forEach(techId -> {
            Entity technology = dataDefinitionService
                    .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY).get(techId);

            if (entity.getBooleanField(L_CHANGE_PERFORMANCE_NORM)) {

                Optional<Entity> productionLine = technologyService.getProductionLine(technology);
                if(!productionLine.isPresent()) {
                    view.addMessage("technologies.changeTechnologyParameters.error.noDefaultProductionLine", ComponentState.MessageType.FAILURE,technology.getStringField(TechnologyFields.NUMBER));
                    throw new IllegalStateException("There was a problem creating the technology");
                }

            }

            technology.setField(TechnologyFields.MASTER, Boolean.FALSE);
            technology = technology.getDataDefinition().save(technology);
            if (technology.isValid()) {
                Entity copyTechnology = technology.getDataDefinition().copy(technology.getId()).get(0);
                Entity product = technology.getBelongsToField(TechnologyFields.PRODUCT);
                copyTechnology.setField(TechnologyFields.NUMBER, technologyNameAndNumberGenerator.generateNumber(product));
                copyTechnology.setField(TechnologyFields.NAME, technologyNameAndNumberGenerator.generateName(product));

                if (entity.getBooleanField(L_CHANGE_GROUP)) {
                    copyTechnology.setField(TechnologyFields.TECHNOLOGY_GROUP, finalGroup);
                }

                if (entity.getBooleanField(L_CHANGE_PERFORMANCE_NORM)) {
                    technologyService.getMasterTechnologyProductionLine(copyTechnology).ifPresent(
                            e -> {
                                e.setField(TechnologyProductionLineFields.STANDARD_PERFORMANCE, finalStandardPerformance);
                                e.getDataDefinition().save(e);
                            }
                    );
                }
                copyTechnology = copyTechnology.getDataDefinition().save(copyTechnology);
                Entity copyTechnologyDb = copyTechnology.getDataDefinition().get(copyTechnology.getId());
                if (updateOperationWorkstations) {
                    List<Entity> tocs = copyTechnologyDb.getHasManyField(TechnologyFields.OPERATION_COMPONENTS);
                    tocs.forEach(toc -> {
                        Entity operation = toc.getBelongsToField(TechnologyOperationComponentFields.OPERATION);
                        toc.setField(TechnologyOperationComponentFields.WORKSTATIONS, operation.getField(OperationFields.WORKSTATIONS));
                        toc.getDataDefinition().save(toc);
                    });
                }
                if (updateOperationTimeNorms) {
                    List<Entity> tocs = copyTechnologyDb.getHasManyField(TechnologyFields.OPERATION_COMPONENTS);
                    tocs.forEach(toc -> {
                        Entity operation = toc.getBelongsToField(TechnologyOperationComponentFields.OPERATION);
                        for (String fieldName : FIELDS_OPERATION) {
                            toc.setField(fieldName, operation.getField(fieldName));
                        }
                        if (operation.getField(NEXT_OPERATION_AFTER_PRODUCED_TYPE) == null) {
                            toc.setField(NEXT_OPERATION_AFTER_PRODUCED_TYPE, "01all");
                        }

                        if (operation.getField(PRODUCTION_IN_ONE_CYCLE) == null) {
                            toc.setField(PRODUCTION_IN_ONE_CYCLE, "1");
                        }

                        if (operation.getField(NEXT_OPERATION_AFTER_PRODUCED_QUANTITY) == null) {
                            toc.setField(NEXT_OPERATION_AFTER_PRODUCED_QUANTITY, "0");
                        }
                        copyOperationWorkstationTimes(toc, operation);
                        toc.getDataDefinition().save(toc);
                    });
                }

                Entity savedTech = copyTechnologyDb.getDataDefinition().get(copyTechnologyDb.getId());
                if (savedTech.isValid()) {
                    technologyStateChangeViewClient.changeState(new ViewContextHolder(view, state),
                            TechnologyStateStringValues.ACCEPTED, savedTech);
                    Entity tech = savedTech.getDataDefinition().get(savedTech.getId());
                    tech.setField(TechnologyFields.MASTER, Boolean.TRUE);
                    tech.getDataDefinition().save(tech);
                } else {
                    throw new IllegalStateException("There was a problem creating the technology");
                }
                technologyStateChangeViewClient.changeState(new ViewContextHolder(view, state),
                        TechnologyStateStringValues.OUTDATED, technology);
            } else {
                throw new IllegalStateException("There was a problem creating the technology");
            }
        });
    }

    private void copyOperationWorkstationTimes(Entity toc, Entity operation) {
        for (Entity operationWorkstationTime : operation.getHasManyField("operationWorkstationTimes")) {
            for (Entity techOperCompWorkstationTime : toc.getHasManyField("techOperCompWorkstationTimes")) {
                if (techOperCompWorkstationTime.getBelongsToField("workstation").getId()
                        .equals(operationWorkstationTime.getBelongsToField("workstation").getId())) {
                    techOperCompWorkstationTime.setField(TPZ, operationWorkstationTime.getField(TPZ));
                    techOperCompWorkstationTime.setField(TJ, operationWorkstationTime.getField(TJ));
                    techOperCompWorkstationTime.setField(TIME_NEXT_OPERATION,
                            operationWorkstationTime.getField(TIME_NEXT_OPERATION));
                    techOperCompWorkstationTime.getDataDefinition().save(techOperCompWorkstationTime);
                    break;
                }
            }
        }
    }

    public void onChangePerformanceNorm(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        CheckBoxComponent changePerformanceNorm = (CheckBoxComponent) state;
        FieldComponent standardPerformance = (FieldComponent) view
                .getComponentByReference(L_STANDARD_PERFORMANCE);
        if (changePerformanceNorm.isChecked()) {
            standardPerformance.setEnabled(true);
        } else {
            standardPerformance.setEnabled(false);
            standardPerformance.setFieldValue(null);
        }

    }

    public void onChangeChangeGroup(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        CheckBoxComponent changeGroup = (CheckBoxComponent) state;
        FieldComponent technologyGroup = (FieldComponent) view.getComponentByReference(L_TECHNOLOGY_GROUP);
        if (changeGroup.isChecked()) {
            technologyGroup.setEnabled(true);
        } else {
            technologyGroup.setEnabled(false);
            technologyGroup.setFieldValue(null);
        }
    }

}
