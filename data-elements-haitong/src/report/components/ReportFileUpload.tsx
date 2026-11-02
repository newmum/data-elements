import Icon from './Icon';

/** Click the file input itself, without hidden-input .click() or showPicker(). */
export default function ReportFileUpload({disabled=false,hasFiles=false,onFiles}:{disabled?:boolean;hasFiles?:boolean;onFiles:(files:File[])=>void}){
  return <label className={`report-file-upload-button${disabled?' is-disabled':''}`}><Icon name="upload" size={18}/><span>{hasFiles?'继续上传数据文件':'上传数据文件'}</span>
    <input type="file" multiple accept=".csv,.xlsx,.xls,.txt,.json" aria-label="上传数据文件" aria-describedby="report-upload-file-requirements" disabled={disabled}
      onChange={event=>{const files=Array.from(event.currentTarget.files||[]);event.currentTarget.value='';if(files.length)onFiles(files);}}/>
  </label>;
}
