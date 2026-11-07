export type Cell = string | number | null;
export type Row = Record<string, Cell>;
export type Column = { name: string; type: string; primary?: boolean; nullable?: boolean; default?: string; comment?: string };
export type TableDef = { name: string; comment: string; columns: Column[]; rows: Row[] };

const id: Column = { name: 'id', type: 'int unsigned', primary: true, nullable: false, comment: '主键，自增' };
const created: Column = { name: 'created_at', type: 'datetime', nullable: false, default: 'CURRENT_TIMESTAMP', comment: '创建时间' };
const names = ['林知远', '陈雨禾', '周予安', '许星澜', '沈明川', '苏念初', '陆景行', '顾清和', '唐以宁', '江书白', '温若溪', '程怀瑾', '叶时安', '宋嘉禾', '白予墨', '夏清妍', '方亦舟', '孟听澜', '乔星野', '梁沐言', '秦疏桐', '何见山', '余南乔', '徐晚晴'];
const productNames = ['机械键盘 K87', '无线鼠标 M3', '27 英寸显示器', 'USB-C 扩展坞', '降噪耳机 H5', '桌面支架', '便携固态硬盘 1TB', '编织数据线', '智能台灯', '笔记本电脑包', '桌面收纳盒', '人体工学座椅'];
const prices = [399, 159, 1899, 269, 699, 129, 599, 49, 229, 189, 79, 1299];
export const initialTables: Record<string, TableDef> = {
  users: {
    name: 'users', comment: '用户账户',
    columns: [id, { name: 'name', type: 'varchar(50)', nullable: false, comment: '姓名' }, { name: 'email', type: 'varchar(120)', nullable: false, comment: '邮箱' }, { name: 'role', type: 'varchar(20)', default: 'member', comment: '用户角色' }, { name: 'status', type: 'varchar(20)', default: 'active', comment: '账户状态' }, created],
    rows: names.map((name, i) => ({ id: i + 1, name, email: `user${String(i + 1).padStart(2, '0')}@example.com`, role: i === 0 ? 'admin' : i % 6 === 0 ? 'editor' : 'member', status: i % 7 === 5 ? 'inactive' : 'active', created_at: `2026-10-${String(i + 1).padStart(2, '0')} ${String(9 + i % 9).padStart(2, '0')}:30:00` })),
  },
  orders: {
    name: 'orders', comment: '商品订单',
    columns: [id, { name: 'user_id', type: 'int unsigned', nullable: false, comment: '关联用户' }, { name: 'product', type: 'varchar(100)', nullable: false, comment: '商品名称' }, { name: 'amount', type: 'decimal(10,2)', nullable: false, comment: '订单金额（元）' }, { name: 'status', type: 'varchar(20)', default: 'pending', comment: '订单状态' }, created],
    rows: Array.from({ length: 15 }, (_, i) => ({ id: 1001 + i, user_id: i % 12 + 1, product: productNames[i % 12], amount: prices[i % 12], status: ['completed', 'completed', 'paid', 'pending', 'cancelled'][i % 5], created_at: `2026-11-${String(i % 6 + 1).padStart(2, '0')} 14:20:00` })),
  },
  products: {
    name: 'products', comment: '商品目录与库存',
    columns: [id, { name: 'name', type: 'varchar(100)', nullable: false, comment: '商品名称' }, { name: 'category_id', type: 'int unsigned', comment: '分类 ID' }, { name: 'price', type: 'decimal(10,2)', nullable: false, comment: '单价（元）' }, { name: 'stock', type: 'int', default: '0', comment: '可用库存' }, { name: 'status', type: 'varchar(20)', default: 'active', comment: '上架状态' }, created],
    rows: productNames.map((name, i) => ({ id: i + 1, name, category_id: i % 4 + 1, price: prices[i], stock: [128, 256, 42, 87, 65, 203, 96, 512, 74, 118, 0, 31][i], status: i === 10 ? 'inactive' : 'active', created_at: '2026-09-15 10:00:00' })),
  },
  categories: {
    name: 'categories', comment: '商品分类',
    columns: [id, { name: 'name', type: 'varchar(50)', nullable: false, comment: '分类名称' }, { name: 'description', type: 'varchar(255)', nullable: true, comment: '分类说明' }, created],
    rows: ['电脑外设', '数码配件', '办公设备', '生活精选'].map((name, i) => ({ id: i + 1, name, description: ['键盘、鼠标与显示设备', '连接、存储与便携配件', '让工作更舒适', null][i], created_at: '2026-09-01 09:00:00' })),
  },
  audit_logs: {
    name: 'audit_logs', comment: '系统操作日志',
    columns: [id, { name: 'user_id', type: 'int unsigned', comment: '操作用户' }, { name: 'action', type: 'varchar(50)', comment: '操作类型' }, { name: 'resource', type: 'varchar(100)', comment: '操作对象' }, { name: 'ip_address', type: 'varchar(45)', comment: 'IP 地址' }, created],
    rows: Array.from({ length: 18 }, (_, i) => ({ id: 501 + i, user_id: i % 5 + 1, action: ['LOGIN', 'VIEW', 'EXPORT'][i % 3], resource: ['console', 'users', 'orders'][i % 3], ip_address: `192.0.2.${i + 10}`, created_at: `2026-11-06 ${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '45' : '15'}:00` })),
  },
};
