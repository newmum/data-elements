// Compatibility exports for older low-code modules. New callers should use runtime-sfc directly.
export {
  registerRuntimeSfc as appComponent,
  loadAllRuntimeSfc as loadDynamicComponent,
} from "@/plugins/compiler/runtime-sfc";
