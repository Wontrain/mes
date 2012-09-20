package com.qcadoo.mes.operationalTasksForOrders.hooks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.Lists;
import com.qcadoo.mes.operationalTasks.constants.OperationalTasksConstants;
import com.qcadoo.mes.operationalTasks.constants.OperationalTasksFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchCriterion;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.search.SearchResult;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SearchRestrictions.class)
public class TechInstOperCompHooksOTFOTest {

    private TechInstOperCompHooksOTFO hooksOTFO;

    @Mock
    private Entity entity, tioc, task;

    @Mock
    private DataDefinition dataDefinition, operationalTasksDD;

    @Mock
    private DataDefinitionService dataDefinitionService;

    @Mock
    private SearchCriteriaBuilder builder;

    @Mock
    private SearchResult result;

    @Before
    public void init() {
        hooksOTFO = new TechInstOperCompHooksOTFO();
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(SearchRestrictions.class);
        ReflectionTestUtils.setField(hooksOTFO, "dataDefinitionService", dataDefinitionService);

        when(
                dataDefinitionService.get(OperationalTasksConstants.PLUGIN_IDENTIFIER,
                        OperationalTasksConstants.MODEL_OPERATIONAL_TASK)).thenReturn(operationalTasksDD);
        when(operationalTasksDD.find()).thenReturn(builder);
        SearchCriterion criterion = SearchRestrictions.belongsTo("technologyInstanceOperationComponent", tioc);
        when(builder.add(criterion)).thenReturn(builder);
        when(builder.list()).thenReturn(result);
    }

    private EntityList mockEntityList(List<Entity> list) {
        EntityList entityList = mock(EntityList.class);
        when(entityList.iterator()).thenReturn(list.iterator());
        return entityList;
    }

    @Test
    public void shouldReturnWhenEntityIdIsNull() throws Exception {
        // given
        when(entity.getId()).thenReturn(null);

        // when
        hooksOTFO.changedDescriptionOperationTasksWhenCommentEntityChanged(dataDefinition, entity);
    }

    @Test
    public void shouldReturnWhenOperationCommentIsThisSame() throws Exception {
        // given
        Long entityId = 1L;
        String entityComment = "comment";
        String tiocComment = "comment";
        when(entity.getId()).thenReturn(entityId);
        when(dataDefinition.get(entityId)).thenReturn(tioc);
        when(entity.getStringField("comment")).thenReturn(entityComment);
        when(tioc.getStringField("comment")).thenReturn(tiocComment);

        // when
        hooksOTFO.changedDescriptionOperationTasksWhenCommentEntityChanged(dataDefinition, entity);
    }

    @Test
    public void shouldChangedOperationaTasksDescriptionWhenOperationCommentWasChanged() throws Exception {
        // given
        Long entityId = 1L;
        String entityComment = "comment";
        String tiocComment = "comment2";
        when(entity.getId()).thenReturn(entityId);
        when(dataDefinition.get(entityId)).thenReturn(tioc);
        when(entity.getStringField("comment")).thenReturn(entityComment);
        when(tioc.getStringField("comment")).thenReturn(tiocComment);

        EntityList tasks = mockEntityList(Lists.newArrayList(task));

        when(result.getEntities()).thenReturn(tasks);
        // when
        hooksOTFO.changedDescriptionOperationTasksWhenCommentEntityChanged(dataDefinition, entity);
        // then
        Mockito.verify(task).setField(OperationalTasksFields.DESCRIPTION, entityComment);
    }
}
