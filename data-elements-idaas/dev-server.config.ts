/** 统一身份管理平台的本地开发与预览地址；与同目录其他前端工程分配不同端口。 */
export const localServer = { host: 'localhost', port: 3005, strictPort: true } as const;
export const localOrigin = `http://${localServer.host}:${localServer.port}`;
