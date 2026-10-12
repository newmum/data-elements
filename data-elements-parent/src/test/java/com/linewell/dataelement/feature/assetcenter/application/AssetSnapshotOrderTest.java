package com.linewell.dataelement.feature.assetcenter.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssetSnapshotOrderTest {
    @Test void resourceCountsAreOrderedOverTheEntireAuthorizedSnapshotBeforePaging() {
        var db=new AssetCenterStore(null,new ObjectMapper(),null);
        var service=spy(new AssetCenterWorkbenchService(db,null,null,null,null));
        List<Map<String,Object>> original=new ArrayList<>();
        for(int i=0;i<150;i++)original.add(new LinkedHashMap<>(Map.of("catalogId",String.format("c%03d",i),"attachmentId","a","itemIds",Collections.nCopies(i,"field"))));
        doAnswer(invocation->new LinkedHashMap<>(Map.of("subscription",new LinkedHashMap<>(Map.of("resources",original,"draft",Map.of("resources",original)))))).when(service).progress("owned");
        Map<String,String> small=Map.of("resourcePage","1","resourceSize","20","resourceSortField","itemCount","resourceSortDir","desc");
        Map<String,String> large=Map.of("resourcePage","1","resourceSize","100","resourceSortField","itemCount","resourceSortDir","desc");
        var a=service.progress("owned",small);var b=service.progress("owned",large);
        var aSub=db.object(a.get("subscription"));var bSub=db.object(b.get("subscription"));
        var aRows=db.array(aSub.get("resources"));var bRows=db.array(bSub.get("resources"));
        assertEquals(aRows,bRows.subList(0,20));assertEquals("c149",aRows.getFirst().get("catalogId"));
        assertEquals(150,db.object(aSub.get("resourcePage")).get("total"));
        assertEquals("c000",original.getFirst().get("catalogId"));
        assertFalse(original.getFirst().containsKey("_identity"));
        assertFalse(aRows.getFirst().containsKey("_count"));
        verify(service,times(2)).progress("owned");
        assertThrows(IllegalArgumentException.class,()->service.progress("owned",Map.of("resourcePage","1","resourceSortField","injected")));
        doThrow(new IllegalStateException("not authorized")).when(service).progress("foreign");
        assertThrows(IllegalStateException.class,()->service.progress("foreign",small));
    }
}
