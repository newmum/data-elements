import { useMemo } from "react";
import { compareLines } from "./lineDiff";

export function DiffView({
  original,
  modified,
}: {
  original: string;
  modified: string;
}) {
  const lines = useMemo(
    () => compareLines(original, modified),
    [original, modified],
  );
  return (
    <div className="fs-diff" role="region" aria-label="版本差异" tabIndex={0}>
      <div className="fs-diff__head">
        <span>旧版本 · − 删除内容</span>
        <span>新版本 · + 新增内容</span>
      </div>
      <div className="fs-diff__scroll">
        {lines.map((line, index) => (
          <div className="fs-diff__row" key={index}>
            <pre
              className={
                line.changed && line.before !== undefined ? "is-removed" : ""
              }
            >
              <i>{line.left}</i>
              <span>
                {line.changed && line.before !== undefined ? "− " : ""}
                {line.before ?? " "}
              </span>
            </pre>
            <pre
              className={
                line.changed && line.after !== undefined ? "is-added" : ""
              }
            >
              <i>{line.right}</i>
              <span>
                {line.changed && line.after !== undefined ? "+ " : ""}
                {line.after ?? " "}
              </span>
            </pre>
          </div>
        ))}
      </div>
    </div>
  );
}
