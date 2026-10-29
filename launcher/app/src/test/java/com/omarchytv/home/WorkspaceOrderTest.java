package com.omarchytv.home;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class WorkspaceOrderTest {
    private static JSONObject order(String... names) throws Exception {
        return new JSONObject().put("order", new JSONArray(Arrays.asList(names)));
    }

    @Test
    public void allFiveInTheUsualOrderByDefault() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), LauncherConfig.workspaceOrder(null));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), LauncherConfig.workspaceOrder(new JSONObject()));
    }

    @Test
    public void leftOutWorkspacesAreHiddenAndTheRestKeepTheirOrder() throws Exception {
        assertEquals(Arrays.asList(1, 3, 4, 5), LauncherConfig.workspaceOrder(order("home", "apps", "pc", "system")));
        assertEquals(Arrays.asList(1, 5, 3), LauncherConfig.workspaceOrder(order("home", "system", "apps")));
    }

    @Test
    public void customNamesForKnownWorkspacesOnlyCleanedAndCapped() throws Exception {
        JSONObject ws = new JSONObject().put("names", new JSONObject()
                .put("pc", "  cachy ").put("apps", "").put("nope", "x").put("system", "a\tvery long name here"));
        java.util.Map<Integer, String> names = LauncherConfig.workspaceNames(ws);
        assertEquals("cachy", names.get(4));
        assertEquals("avery long n", names.get(5));
        assertEquals(2, names.size());
        assertEquals(0, LauncherConfig.workspaceNames(null).size());
    }

    @Test
    public void unknownAndRepeatedNamesAreDroppedAndHomeIsKept() throws Exception {
        assertEquals(Arrays.asList(1, 4, 3), LauncherConfig.workspaceOrder(order("PC", "nope", "pc", " apps ")));
        assertEquals(Arrays.asList(3, 1), LauncherConfig.workspaceOrder(order("apps", "home")));
    }
}
