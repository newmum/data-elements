import { expect, it } from "vitest";
import { compareLines } from "./lineDiff";
it("aligns shared lines while preserving every removed and added line", () => {
  const result = compareLines("a\nold\nz", "a\nnew\nz");
  expect(
    result
      .filter((line) => line.left)
      .map((line) => line.before)
      .join("\n"),
  ).toBe("a\nold\nz");
  expect(
    result
      .filter((line) => line.right)
      .map((line) => line.after)
      .join("\n"),
  ).toBe("a\nnew\nz");
  expect(result.filter((line) => line.changed)).toHaveLength(2);
  expect(result.at(-1)).toMatchObject({
    before: "z",
    after: "z",
    changed: false,
  });
});
it("bounds unusually large comparisons without dropping either version", () => {
  const original = Array.from({ length: 1100 }, (_, i) => `old-${i}`).join(
    "\n",
  );
  const modified = Array.from({ length: 1200 }, (_, i) => `new-${i}`).join(
    "\n",
  );
  const result = compareLines(original, modified);
  expect(result.filter((line) => line.left)).toHaveLength(1100);
  expect(result.filter((line) => line.right)).toHaveLength(1200);
});
