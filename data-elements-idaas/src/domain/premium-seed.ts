import type { Database, Base, Catalog, Domain, Log, User } from './types.ts';
/** Deterministic, referentially linked fictional data. All dates are relative to initialization. */
export function enrichSeed(db: Database, now: Date): Database {
    const day = 86400000;
    const at = (ago: number) => new Date(now.getTime() - ago * day).toISOString();
    const base = (id: string, name: string, domain: Domain = 'workforce', status = 'enabled'): Base => ({ id, name, domain, status, createdAt: at(120), updatedAt: at(2), version: 1 });
    const surnames = ['李', '王', '张', '刘', '陈', '杨', '黄', '赵', '吴', '周', '徐', '孙', '胡', '朱', '高', '林', '何', '郭', '马', '罗', '梁', '宋', '郑', '谢'];
    const given = ['嘉宁', '文博', '思远', '雨桐', '佳怡', '子涵', '明哲', '一诺', '志诚', '晓宇', '博文', '沐阳', '欣悦', '逸凡', '梓萱'];
    const posts = ['研发工程师', '产品经理', '数据专员', '部门负责人'];
    for (let i = 37; i <= 180; i++) {
        const orgId = `o${1 + i % 11}`, post = posts[i % 4];
        db.users.push({ ...base(`u${i}`, surnames[i % 24] + given[Math.floor(i / 24) % given.length]), account: `staff${String(i).padStart(4, '0')}`, email: `staff${i}@example.com`, phone: `138${String(10000000 + i)}`, orgId, post, kind: 'person', locked: i % 41 === 0, appointments: [{ orgId, post, primary: true }], verified: true, history: [{ time: at(100 + i % 60), text: '由人力资源目录创建统一身份' }, { time: at(30 + i % 10), text: '同步主职机构与岗位信息' }], status: i % 29 === 0 ? 'disabled' : i % 53 === 0 ? 'pending' : 'enabled' });
    }
    for (let i = 16; i <= 60; i++) {
        db.users.push({ ...base(`p${i}`, surnames[(i + 4) % 24] + given[Math.floor(i / 24) + 6], 'public', i % 23 === 0 ? 'disabled' : 'enabled'), account: `citizen${String(i).padStart(3, '0')}`, email: `citizen${i}@example.org`, phone: `139${String(10000000 + i)}`, orgId: '', post: '', kind: 'citizen', locked: false, appointments: [], verified: i % 4 !== 0, history: [{ time: at(80 + i % 20), text: '通过统一注册入口创建自然人账户' }] });
    }
    db.users.forEach((u, i) => {
        u.createdAt = at(150 + i % 150);
        u.updatedAt = at(i % 12);
        u.employeeId = u.kind === 'person' ? `XH-${String(i + 1).padStart(5, '0')}` : undefined;
        u.employmentType = u.kind === 'person' ? (i % 9 === 0 ? 'temporary' : i % 5 === 0 ? 'partner' : 'employee') : undefined;
        u.mfaEnabled = u.kind === 'admin' || i % 7 !== 0;
        u.joinDate = at(120 + i % 180).slice(0, 10);
    });
    db.apps.forEach((a, i) => {
        a.createdAt = at(100 + i * 8);
        a.updatedAt = at(i % 5);
        a.description = [
            '统一管理数据目录、血缘关系与治理流程，为企业建立可信的数据底座。',
            '连接组织、人员与协同流程，让审批、日程和信息流转高效完成。',
            '汇聚企业数据资产，提供统一检索、授权申请与资产全景服务。',
            '覆盖项目立项、任务协作与里程碑管理，连接跨部门交付团队。',
            '统一财务核算与共享服务，按岗位管理敏感业务系统的访问资格。',
            '整合企业常用服务，一次身份认证即可访问已授权的办公应用。',
            '聚合运营数据与业务指标，为管理决策提供统一的分析入口。',
            '沉淀团队知识与经验，构建可检索、可协作的企业知识空间。',
            '统一员工资料、考勤和人事服务，与身份中心持续同步人员变化。',
            '为自然人提供便民事项查询与在线服务，统一注册与登录体验。',
            '面向企业经办人员，集中提供法人服务与业务办理入口。',
            '汇聚公众服务事项，提供统一的账号、认证与业务访问体验。'
        ][i];
    });
    // Populate grants without ever granting application access to the platform administrator.
    for (let i = 1; i <= 180; i++) {
        const appId = `a${1 + i % 6}`;
        const u = db.users.find(v => v.id === `u${i}`)!;
        if (!db.grants.some(g => g.source === 'user' && g.subjectId === u.id && g.appId === appId))
            db.grants.push({ ...base(`premium-grant-${i}`, '岗位应用访问授权'), source: 'user', subjectId: u.id, appId, roleIds: [`${appId}-r${i % 4 === 0 ? 2 : 1}`], includeChildren: false, startsAt: at(90), expiresAt: i % 13 === 0 ? at(-7 - i % 20) : i % 31 === 0 ? at(3) : null, reason: u.employmentType === 'partner' ? '外部项目协作，按期限授予访问资格' : '岗位履职与日常业务协作' });
    }
    for (let i = 16; i <= 60; i++)
        db.grants.push({ ...base(`premium-pgrant-${i}`, '公众服务访问资格', 'public'), source: 'user', subjectId: `p${i}`, appId: `a${10 + i % 3}`, roleIds: [`a${10 + i % 3}-r1`], includeChildren: false, startsAt: at(50), expiresAt: null, reason: '公众业务服务访问' });
    const catalogs: Pick<Catalog, 'category' | 'name' | 'code' | 'description' | 'value' | 'subjectType' | 'orgId' | 'userId' | 'appId' | 'permissions'>[] = [
        { category: 'extension', name: '成本中心', code: 'cost_center', description: '用于关联财务成本中心与任职信息', value: '文本', subjectType: '机构', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'extension', name: '数据敏感级别', code: 'sensitivity_level', description: '标识应用资源的敏感程度', value: '枚举', subjectType: '资源', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'extension', name: '合同到期日期', code: 'contract_expiry', description: '外部合作人员合同到期参考字段', value: '日期', subjectType: '用户', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'extension', name: '是否关键应用', code: 'critical_app', description: '标记对业务连续性有关键影响的应用', value: '布尔', subjectType: '应用', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'dictionary', name: '高级工程师', code: 'title-senior', description: '专业技术职称', value: '职称', subjectType: '', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'dictionary', name: '部门总监', code: 'duty-director', description: '组织管理职务', value: '职务', subjectType: '', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'dictionary', name: 'P7', code: 'grade-p7', description: '专业序列职级', value: '职级', subjectType: '', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'dictionary', name: 'P6', code: 'grade-p6', description: '专业序列职级', value: '职级', subjectType: '', orgId: '', userId: '', appId: '', permissions: [] },
        { category: 'line', name: '信息安全', code: 'line-security', description: '身份与信息安全管理条线', value: '', subjectType: '', orgId: 'o11', userId: '', appId: '', permissions: [] },
        { category: 'line', name: '客户服务', code: 'line-service', description: '客户服务与运营支持条线', value: '', subjectType: '', orgId: 'o8', userId: '', appId: '', permissions: [] },
        { category: 'delegate', name: '华东机构管理员', code: 'delegate-east', description: '管理华东分公司及下级人员', value: '包含下级', subjectType: '', orgId: 'o7', userId: 'u8', appId: '', permissions: ['users:write', 'orgs:write'] },
        { category: 'delegate', name: '华南机构管理员', code: 'delegate-south', description: '维护华南区域组织与账户', value: '包含下级', subjectType: '', orgId: 'o9', userId: 'u10', appId: '', permissions: ['users:write', 'orgs:write'] },
        { category: 'api-grant', name: '门户读取身份目录', code: 'portal-directory-read', description: '只读访问已授权用户与组织基本信息', value: '', subjectType: 'app', orgId: '', userId: '', appId: 'a6', permissions: ['users:read', 'orgs:read'] },
        { category: 'api-grant', name: '项目平台读取角色', code: 'project-role-read', description: '同步应用角色与授权信息', value: '', subjectType: 'app', orgId: '', userId: '', appId: 'a4', permissions: ['roles:read', 'grants:read'] },
        { category: 'api-grant', name: '审计专员只读接口', code: 'auditor-read', description: '按工作职责读取审计信息', value: '', subjectType: 'user', orgId: 'o11', userId: 'u12', appId: '', permissions: ['audit:read'] },
        { category: 'api-grant', name: '数据维护查询接口', code: 'data-steward-read', description: '业务数据维护人员读取角色清单', value: '', subjectType: 'user', orgId: 'o4', userId: 'u4', appId: '', permissions: ['roles:read'] },
        { category: 'admin', name: '安全审计员', code: 'auditor', description: '平台审计日志只读访问', value: '', subjectType: '', orgId: 'o11', userId: 'u12', appId: '', permissions: ['auditor'] },
        { category: 'admin', name: '应用管理员', code: 'appmanager', description: '管理已分配的业务应用', value: '', subjectType: '', orgId: 'o3', userId: 'u3', appId: 'a1', permissions: ['appmanager'] },
        { category: 'admin', name: '机构管理员', code: 'orgadmin', description: '数字化中心及下级机构管理', value: '', subjectType: '', orgId: 'o1', userId: 'u2', appId: '', permissions: ['orgadmin'] },
    ];
    catalogs.forEach((c, i) => db.catalog.push({ ...base(`premium-cat-${i}`, c.name), parentId: '', memberIds: [], expiresAt: '', ...c }));
    const businessUsers = db.users.filter(u => u.kind === 'person'), publicUsers = db.users.filter(u => u.kind === 'citizen');
    const logs: Log[] = [];
    for (let i = 0; i < 1800; i++) {
        const domain: Domain = i % 4 === 0 ? 'public' : 'workforce';
        const app = domain === 'public' ? db.apps[9 + (i % 3)] : db.apps[(i * 7 + Math.floor(i / 10)) % 8];
        const us = domain === 'public' ? publicUsers : businessUsers;
        const user = us[(i * 7 + Math.floor(i / 13)) % us.length];
        const type: Log['type'] = i % 6 === 0 ? 'api' : i % 5 === 0 ? 'operation' : 'login';
        const failed = i % 37 === 0 || i % 79 === 0;
        const time = new Date(now.getTime() - (i % 14) * day - ((i * 43) % 720) * 60000).toISOString();
        const action = type === 'api' ? ['查询身份信息', '查询组织机构', '查询应用角色'][i % 3] : type === 'operation' ? ['调整应用授权', '更新用户资料', '维护应用配置', '新增权限组', '更新安全策略'][Math.floor(i / 5) % 5] : i % 7 === 0 ? '多因子认证登录' : '账号密码登录';
        const before = type === 'operation' ? JSON.stringify({ status: 'enabled', role: '业务查看者', version: 1 }, null, 2) : '—';
        const after = type === 'operation' ? (failed ? before : JSON.stringify({ status: 'enabled', role: i % 2 ? '业务维护者' : '业务查看者', version: 2 }, null, 2)) : '—';
        logs.push({ ...base(`premium-log-${i}`, action, domain), createdAt: time, updatedAt: time, type, actor: type === 'operation' ? (domain === 'workforce' ? 'admin' : 'citizen001') : user.account, module: type === 'login' ? '统一认证' : type === 'api' ? '开放接口' : action.includes('授权') ? '授权管理' : '身份管理', action, target: app.name, result: failed ? '失败' : '成功', ip: `192.0.2.${1 + i % 220}`, appId: app.id, traceId: `trace_${String(i + 1).padStart(10, '0')}`, before, after, risk: failed ? 'high' : type === 'operation' ? 'medium' : 'low', device: i % 6 === 0 ? 'Windows · Edge' : i % 5 === 0 ? 'iOS · Safari' : 'Windows · Chrome', location: '企业网络', httpMethod: type === 'api' ? 'GET' : 'POST', requestPath: type === 'login' ? '/api/auth/login' : type === 'api' ? '/api/identity/users' : '/api/management/records', httpStatus: failed ? 403 : 200, durationMs: 45 + (i * 11) % 430 });
    }
    db.logs = logs.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return db;
}
