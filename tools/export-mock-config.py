"""只读导出当前数据库生效的公开门户配置，供离线 Mock 使用。"""
import argparse
import json
from pathlib import Path
import urllib.parse
import urllib.request
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'mock/scenarios/configuration.json'
SCENARIOS = {
    'PUBLIC_SECURITY': 'police',
    'BROADCASTING': 'broadcasting',
    'SEA_FISHERY': 'sea-fishery',
    'CHENGTIAN_DATA_PLATFORM': 'chengtian',
}
# 仅导出登录前公开配置，保持实际类型；不读取运行配置、账号或连接信息。
PUBLIC_FIELDS = {
    'appId', 'systemName', 'systemCode', 'showShoppingCar', 'needLogin',
    'version', 'showTagsView', 'showLogo', 'showWatermark', 'watermarkContent',
    'themeColor', 'logo', 'loginUrl', 'sidebarOpen',
}


def export_structure(tenants):
    """按当前真实租户绑定读取菜单/机构白名单，不读取用户、成员或凭据字段。"""
    import pymysql
    modules = []
    for name in ['resource-sync', 'export-db']:
        spec = importlib.util.spec_from_file_location(name, ROOT / 'tools' / (name + '.py'))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        modules.append(module)
    config = modules[0].read_json(modules[0].PRIVATE_CONFIG)
    sources = {key: (kind, options) for key, kind, _, options in modules[1].connections(config)}
    bindings = config['nacos_tenant_database']['tenant-bindings']
    for item in tenants:
        tenant_id = item['tenant']['tid']
        kind, options = sources[bindings[tenant_id]]
        if kind != 'mysql':
            raise ValueError('当前配置导出仅实现本次 MySQL 运行环境，不猜测其他方言')
        connection = pymysql.connect(**options, cursorclass=pymysql.cursors.DictCursor, connect_timeout=10)
        try:
            with connection.cursor() as cursor:
                cursor.execute('SET TRANSACTION READ ONLY')
                cursor.execute('''SELECT id,name,parent_id AS parentId,static_icon AS staticIcon,
                    status,resource_type AS resourceType,sno,url,code,open_type AS openType,
                    sort_num AS sortNum FROM rm_menu_t
                    WHERE tenant_id=%s AND app_id=%s AND deleted=0 AND resource_type<>1
                    ORDER BY sort_num,id''', (tenant_id, item['settings']['appId']))
                item['menus'] = cursor.fetchall()
                cursor.execute('''SELECT id,name,parent_id AS parentId,serial_number AS serialNumber,
                    org_path AS orgPath,sort_num AS sortNum,status FROM rm_org_t
                    WHERE tenant_id=%s AND deleted=0 ORDER BY sort_num,id''', (tenant_id,))
                item['organizations'] = cursor.fetchall()
        finally:
            connection.rollback()
            connection.close()


def public_settings(value):
    if not isinstance(value, dict):
        raise ValueError('公开配置不是对象')
    result = {key: val for key, val in value.items() if key in PUBLIC_FIELDS}
    if not result.get('appId') or not result.get('systemName'):
        raise ValueError('缺少公开应用标识或平台名称')
    if result.get('logo') and not result['logo'].startswith('data:image/'):
        raise ValueError('离线配置的 Logo 须先转换为本地图片或 data:image，不能保留远端依赖')
    return result


def export_configuration(base_url, with_structure=False):
    def get(path):
        with urllib.request.urlopen(base_url.rstrip('/') + path, timeout=30) as response:
            payload = json.load(response)
        if payload.get('code') not in [0, 200]:
            raise ValueError('公开配置接口返回失败')
        return payload['data']

    options = get('/sym/tenant/login-options')
    if not isinstance(options, list) or len(options) > 100:
        raise ValueError('行业选项格式或规模不合法')
    tenants = []
    for option in options:
        code = option.get('code')
        if code not in SCENARIOS:
            raise ValueError('出现未登记的行业编码，请先审阅场景对应关系')
        tenant = {key: option[key] for key in ['tid', 'code', 'name']}
        if not all(isinstance(value, str) and value for value in tenant.values()):
            raise ValueError('租户标识必须保持字符串')
        # 各租户实际分库，仅对明确的四个行业分别读取一次公开配置。
        query = urllib.parse.urlencode({'tenantId': tenant['tid']})
        tenants.append({
            'scenario': SCENARIOS[code],
            'tenant': tenant,
            'settings': public_settings(get('/sym/tenant/login-settings?' + query)),
        })
    if {item['scenario'] for item in tenants} != set(SCENARIOS.values()):
        raise ValueError('四个行业配置不完整')
    if len({item['tenant']['tid'] for item in tenants}) != len(tenants):
        raise ValueError('行业租户标识重复')
    if with_structure:
        export_structure(tenants)
    return {
        'version': 1,
        'source': '当前数据库生效的门户公开配置及租户菜单/组织白名单' if with_structure else '当前数据库生效的登录前公开配置；通过只读门户接口导出白名单字段',
        'globalSettings': public_settings(get('/portal/index')),
        'tenants': tenants,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8088')
    parser.add_argument('--check', action='store_true', help='只比较当前运行配置与本地，不写文件')
    parser.add_argument('--with-structure', action='store_true', help='同时只读导出当前租户库菜单与组织机构白名单')
    args = parser.parse_args()
    existing = json.loads(OUTPUT.read_text(encoding='utf-8')) if OUTPUT.exists() else {}
    with_structure = args.with_structure or any('menus' in row for row in existing.get('tenants', []))
    configuration = export_configuration(args.base_url, with_structure)
    if args.check:
        existing = json.loads(OUTPUT.read_text(encoding='utf-8'))
        if existing != configuration:
            raise SystemExit('公开配置与本地 Mock 不一致，请审阅后重新导出')
        print('四行业及全局公开配置与当前数据库生效值一致')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(json.dumps(configuration, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('已保存四行业及全局公开配置，未修改服务器或数据库')


if __name__ == '__main__':
    main()
