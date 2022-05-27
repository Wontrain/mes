package com.qcadoo.mes.productFlowThruDivision.hooks;

import java.util.List;
import java.util.Objects;

import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.productFlowThruDivision.constants.OrderFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.ParameterFieldsPFTD;
import com.qcadoo.mes.productionCounting.constants.OrderFieldsPC;
import com.qcadoo.mes.productionCounting.constants.TechnologyFieldsPC;
import com.qcadoo.mes.productionCounting.constants.TypeOfProductionRecording;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderHooksPFTD {

    @Autowired
    private ParameterService parameterService;

    public void onCreate(final DataDefinition orderDD, final Entity order) {
        order.setField(OrderFieldsPFTD.IGNORE_MISSING_COMPONENTS,
                parameterService.getParameter().getBooleanField(ParameterFieldsPFTD.IGNORE_MISSING_COMPONENTS));
    }

    public void onSave(final DataDefinition orderDD, final Entity order) {
        cleanUpOnProductionRecordingTypeChange(orderDD, order);
    }

    private void cleanUpOnProductionRecordingTypeChange(final DataDefinition orderDD, final Entity order) {
        if (Objects.isNull(order.getId())) {
            return;
        }

        Entity orderDB = orderDD.get(order.getId());

        String typeOfProductionRecording = order.getStringField(OrderFieldsPC.TYPE_OF_PRODUCTION_RECORDING);
        String typeOfProductionRecordingDB = orderDB.getStringField(OrderFieldsPC.TYPE_OF_PRODUCTION_RECORDING);
        if (Objects.nonNull(typeOfProductionRecordingDB) && TypeOfProductionRecording.CUMULATED.getStringValue().equals(typeOfProductionRecordingDB)
                && Objects.isNull(typeOfProductionRecording)
                || Objects.nonNull(typeOfProductionRecording) && !typeOfProductionRecording.equals(typeOfProductionRecordingDB)
                && !TypeOfProductionRecording.CUMULATED.getStringValue().equals(typeOfProductionRecording)) {

            order.setField(OrderFields.STAFF, null);
        }
    }
}
