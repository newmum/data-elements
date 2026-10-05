import { defineConfig, loadEnv } from 'vite';
export default defineConfig(({ mode }) => {
 const env = loadEnv(mode, process.cwd(), '');
 return { base:'./', server:{host:'localhost',port:3010,strictPort:true,proxy:{'/dev-api':{target:env.VITE_DATA_ELEMENTS_API_TARGET || 'http://localhost:8088',changeOrigin:true,rewrite:path=>path.replace(/^\/dev-api/,'')}}},preview:{host:'localhost',port:4173},worker:{format:'es'},build:{target:'es2022',sourcemap:false,manifest:true} };
});
