package com.linewell.dataelement.platform.persistence;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TablePreviewOrderControllerTest {
    @Test void ordersTheWholeDraftBeforeTwentyAndHundredPagesWithNoObjectLookup(){
        var rows=new ArrayList<TablePreviewOrderController.Item>();
        for(int i=0;i<150;i++)rows.add(new TablePreviewOrderController.Item("draft-"+i,149-i));
        var small=TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"number","asc",1,20));
        var large=TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"number","asc",1,100));
        assertThat(small.get("total")).isEqualTo(150);
        @SuppressWarnings("unchecked") var twenty=(List<String>)small.get("keys");
        @SuppressWarnings("unchecked") var hundred=(List<String>)large.get("keys");
        assertThat(twenty).hasSize(20).containsExactlyElementsOf(hundred.subList(0,20));
        assertThat(twenty.getFirst()).isEqualTo("draft-149");
        assertThat(new HashSet<>(hundred)).isSubsetOf(rows.stream().map(TablePreviewOrderController.Item::id).toList());
        assertThat(TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"number","asc",20,20)).get("keys")).isEqualTo(List.of());
        assertThat((List<?>)TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"number","asc",1,20,true)).get("keys"))
            .hasSize(150);
    }
    @Test void rejectsInvalidValuesDuplicateKeysAndOversizedPayloadsAndKeepsNullTimesLast(){
        var rows=List.of(new TablePreviewOrderController.Item("a",null),new TablePreviewOrderController.Item("b","2026-10-01T00:00:00Z"),new TablePreviewOrderController.Item("c","2026-10-02T00:00:00Z"));
        assertThat(TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"time","desc",1,20)).get("keys")).isEqualTo(List.of("c","b","a"));
        assertThatThrownBy(()->TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(Collections.nCopies(1001,rows.getFirst()),"text","asc",1,20))).hasMessageContaining("1000");
        assertThatThrownBy(()->TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(List.of(rows.getFirst(),rows.getFirst()),"text","asc",1,20))).hasMessageContaining("重复");
        assertThatThrownBy(()->TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(List.of(new TablePreviewOrderController.Item("a",Map.of("password","forbidden"))),"text","asc",1,20))).hasMessageContaining("排序值");
        assertThatThrownBy(()->TablePreviewOrderController.ordered(new TablePreviewOrderController.Query(rows,"time","SQL",1,20))).hasMessageContaining("排序方向");
    }
}
