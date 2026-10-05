package com.linewell.dataelement.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.model.Menu;
import com.linewell.dataelement.model.RouteMenu;

/**
 * @author zwenbo
 * @Description: 菜单树形结构构建工具类
 * @date 2025/11/28
 */
public class MenuTreeUtil {
    //需要是静态方法 magic才能调用
    public static List<RouteMenu> getRouteMenus(String json) {
        List<Menu> menus = JSONUtil.toList(json, Menu.class);
        List<RouteMenu> routeMenus = new ArrayList<>();
        for (Menu menu : menus) {
            RouteMenu routeMenu = new RouteMenu();
            routeMenu.setId(menu.getId());
            routeMenu.setParentId(menu.getParentId());
            routeMenu.setPath(menu.getUrl());
            if (StrUtil.isNotEmpty(menu.getSno())) {
                if (menu.getSno().startsWith("inner_")) {
                    //去掉前缀
                    routeMenu.setComponent(menu.getSno().substring(6));
                }
                else if (menu.getSno().endsWith("dynamic_")) {
                    //去掉前缀
                    routeMenu.setComponentName(menu.getSno().substring(8));
                } else {
                    routeMenu.setComponentName(menu.getSno());
                }
            }
            //防止重复添加
            routeMenu.setName(menu.getId());
            //keepalive 默认true, 切换标签不刷新
            RouteMenu.Meta meta = RouteMenu.Meta.builder().title(menu.getName()).icon(menu.getStaticIcon()).keepAlive(true).build();
            if(menu.getOpenType() == 1){
                meta.setOpenMode("iframe");
            }else if(menu.getOpenType() == 2){
                meta.setOpenMode("link");
            }
            routeMenu.setMeta(meta);
            routeMenus.add(routeMenu);
        }
        return buildTree(routeMenus);
    }


    /**
     * 将菜单列表构建成树形结构
     *
     * @param menus 菜单列表
     * @return 树形结构的菜单列表
     */
    public static List<RouteMenu> buildTree(List<RouteMenu> menus) {
        // 过滤出顶级菜单（parentId为"ROOT"或null的菜单）
        List<RouteMenu> rootMenus =
            menus.stream().filter(menu -> "ROOT".equals(menu.getParentId()) || menu.getParentId() == null || "".equals(menu.getParentId())).collect(Collectors.toList());

        // 递归构建每个顶级菜单的子树
        for (RouteMenu rootMenu : rootMenus) {
            buildChildren(rootMenu, menus);
        }

        return rootMenus;
    }

    /**
     * 递归构建菜单的子树
     *
     * @param parent 父级菜单
     * @param menus  所有菜单列表
     */
    private static void buildChildren(RouteMenu parent, List<RouteMenu> menus) {
        // 查找当前菜单的子菜单
        List<RouteMenu> children =
            menus.stream().filter(menu -> menu.getParentId() != null && menu.getParentId().equals(parent.getId())).collect(Collectors.toList());

        // 如果有子菜单，则递归构建子菜单的子树
        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (RouteMenu child : children) {
                buildChildren(child, menus);
            }
        }
    }
}
