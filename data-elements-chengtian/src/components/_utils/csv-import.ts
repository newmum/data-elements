/** 将 CSV 作为纯文本单元格交给表格原生 HTML 导入器，保留 JSON 引号、换行与前导零。 */
export function csvToTableHtml(source: string): string {
  const text = source.replace(/^\uFEFF/, "");
  const rows: string[][] = [];
  let row: string[] = [],
    cell = "",
    quoted = false,
    closed = false;
  const finishCell = () => {
    row.push(cell);
    cell = "";
    closed = false;
  };
  const finishRow = () => {
    finishCell();
    if (row.some((value) => value !== "")) rows.push(row);
    row = [];
  };
  for (let i = 0; i < text.length; i++) {
    const character = text[i];
    if (character === '"') {
      if (quoted && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (quoted) {
        quoted = false;
        closed = true;
      } else if (!cell && !closed) quoted = true;
      else throw new Error("CSV 引号格式不正确，请使用导出的参数模板填写后再导入");
    } else if (!quoted && character === ",") finishCell();
    else if (!quoted && (character === "\n" || character === "\r")) {
      finishRow();
      if (character === "\r" && text[i + 1] === "\n") i++;
    } else if (closed) {
      if (!/\s/.test(character)) throw new Error("CSV 引号后存在多余内容，请检查文件格式");
    } else cell += character;
  }
  if (quoted) throw new Error("CSV 存在未闭合的引号，请检查文件格式");
  if (cell || row.length || closed) finishRow();
  if (!rows.length) throw new Error("CSV 文件为空，请填写参数后再导入");
  const escape = (value: string) =>
    value.replace(
      /[&<>"'\r\n]/g,
      (character) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
          "\r": "&#13;",
          "\n": "&#10;",
        })[character] || character
    );
  const [header, ...data] = rows;
  if (data.some((values) => values.length > header.length))
    throw new Error("CSV 数据列超过表头列数，请检查逗号与引号");
  return `<table><thead><tr>${header.map((value) => `<th>${escape(value)}</th>`).join("")}</tr></thead><tbody>${data.map((values) => `<tr>${values.map((value) => `<td>${escape(value)}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
}
