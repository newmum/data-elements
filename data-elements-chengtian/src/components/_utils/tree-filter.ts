const deep = (list: OptionType[], text: string) => {
  const newList: OptionType[] = [];
  list.forEach((data) => {
    const { label, children, isLeaf } = data;
    let isA = false;
    isA = label.includes(text);
    if (isA) {
      newList.push(data);
    } else if (!isLeaf && children) {
      const result = deep(children, text);
      if (result.length) {
        isA = true;
      }
      isA &&
        newList.push(
          Object.assign<{}, OptionType, Partial<OptionType>>({}, data, {
            children: result,
          })
        );
    }
  });
  return newList;
};

export const useTreeFilter = (list: OptionType[], filterText: string) => {
  return filterText ? deep(list, filterText) : list;
};
