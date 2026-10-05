import { useDictStoreHook } from "@/store";
import request from "@/utils/request";

export function setupDict() {
  const dictStore = useDictStoreHook();
  request({
    url: "/sym/dict",
  }).then((data: any) => {
    dictStore.loadAllDict(data);
  });
}
