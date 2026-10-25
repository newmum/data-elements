package com.linewell.dataelement.platform.magic;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import static org.junit.jupiter.api.Assertions.*;

/** Compile the real selected sources; a syntax check does not claim runtime publication. */
class LoggingMagicSyntaxTest {
    @Test void selectedLoggingAndAuthenticationResourcesCompile()throws Exception{
        List<Path> files=new ArrayList<>();
        for(String directory:List.of("function/09.租户账号","api/13.统一身份管理/34.日志管理"))try(var paths=Files.list(Path.of("../magic/"+directory))){files.addAll(paths.filter(path->path.toString().endsWith(".ms")).toList());}
        for(String source:List.of("function/01.通用函数/22.记录业务操作.ms","function/13.统一身份管理/02.功能与对象范围校验.ms","function/13.统一身份管理/03.记录脱敏审计.ms","function/13.统一身份管理/09.接口访问与审计封装.ms","function/13.统一身份管理/32.日志任务权限重校验.ms","function/13.统一身份管理/33.日志请求权限.ms","api/01.系统门户/02.单点登录.ms","api/01.系统门户/04.系统登出.ms","api/09.系统管理/04.机构管理/02.机构保存修改.ms","api/09.系统管理/04.机构管理/05.机构删除.ms","api/13.统一身份管理/01.认证会话/01.登录管理控制台.ms","api/13.统一身份管理/01.认证会话/02.退出管理控制台.ms","api/13.统一身份管理/27.认证账号会话/02.登录统一认证账号.ms","api/13.统一身份管理/27.认证账号会话/03.退出统一认证会话.ms","api/13.统一身份管理/28.多因子认证/05.验证登录二次口令.ms"))files.add(Path.of("../magic/"+source));
        for(Path file:files){String script=Files.readString(file).split("================================",2)[1];assertDoesNotThrow(()->MagicScript.create(script,null).compile(),file.toString());}
    }
}
