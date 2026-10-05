import request from "@/utils/request";

// 操作桶默认为 attachment
const FileAPI = {
  /** 上传文件 （传入 FormData，上传进度回调） */
  upload(formData: FormData, onProgress?: (percent: number) => void) {
    return request<any, FileInfo>({
      url: "/data/file?action=upload",
      method: "post",
      data: formData,
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress?.(percent);
        }
      },
    });
  },

  /** 上传文件（传入 File） */
  uploadFile(file: File) {
    const formData = new FormData();
    formData.append("file", file);
    return request<any, FileInfo>({
      url: "/data/file?action=upload",
      method: "post",
      data: formData,
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  /** 上传文件（传入 base64 字符串） */
  async uploadBase64(base64: string, fileName: string, mimeType: string) {
    const response = await fetch(base64);
    const blob = await response.blob();
    const file = new File([blob], fileName, { type: mimeType });
    const formData = new FormData();
    formData.append("file", file);
    return request<any, FileInfo>({
      url: "/data/file?action=upload",
      method: "post",
      data: formData,
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  /** 删除文件 */
  delete(tid?: string) {
    return request({
      url: "/data/file?action=delete&tid=" + tid,
      method: "post",
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  /** 下载文件 */
  download(url: string, fileName?: string) {
    return request({
      url,
      method: "get",
      responseType: "blob",
    }).then((res) => {
      const blob = new Blob([res.data]);
      const a = document.createElement("a");
      const urlObject = window.URL.createObjectURL(blob);
      a.href = urlObject;
      a.download = fileName || "下载文件";
      a.click();
      window.URL.revokeObjectURL(urlObject);
    });
  },
};

export default FileAPI;

export interface FileInfo {
  name: string;
  url: string;
  tid?: string;
}
