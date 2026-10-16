import { useEffect, useRef, type ComponentType } from 'react';
import { ArrowRightOutlined, BarcodeOutlined, BranchesOutlined, CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, CodeOutlined, DatabaseOutlined, EditOutlined, FieldTimeOutlined, FunctionOutlined, IdcardOutlined, MergeCellsOutlined, NumberOutlined, RetweetOutlined, SafetyCertificateOutlined, SwapOutlined, ThunderboltOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { RULE_CHOICE_GROUPS } from './mappingRulePresentation';

const RULE_ICONS: Record<string, ComponentType> = {
  DIRECT: ArrowRightOutlined, CONSTANT: EditOutlined, UUID32: NumberOutlined,
  UUID: BarcodeOutlined, CURRENT_TIME: ClockCircleOutlined, CAST: SwapOutlined,
  FUNCTION: FunctionOutlined, DATE_PARSE: CalendarOutlined, DATE_FORMAT: FieldTimeOutlined,
  ID_CARD_15_TO_18: IdcardOutlined, ID_CARD_VALIDATE: SafetyCertificateOutlined,
  CONCAT: MergeCellsOutlined, LOOKUP: DatabaseOutlined, LOOKUP_MULTI: UnorderedListOutlined,
  STATIC_MAP: RetweetOutlined, EXPRESSION: CodeOutlined, SYSTEM: ThunderboltOutlined,
  CONDITIONAL: BranchesOutlined, VALIDATE: CheckCircleOutlined,
};

export function MappingRuleIcon({ mode }: { mode: string }) {
  const Icon = RULE_ICONS[mode] ?? FunctionOutlined;
  return <span aria-hidden="true"><Icon /></span>;
}

/** This catalog only selects the existing rule modes; it never creates a second rule definition. */
export default function MappingRuleCatalog({ mode, onChange }: { mode: string; onChange: (mode: string) => void }) {
  const selected = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const reveal = () => selected.current?.scrollIntoView({ block: 'nearest' });
    const observer = new ResizeObserver(reveal);
    if (list.current) observer.observe(list.current);
    reveal();
    return () => observer.disconnect();
  }, [mode]);
  const count = RULE_CHOICE_GROUPS.reduce((total, group) => total + group.options.length, 0);
  return <nav className="fm-rule-catalog" aria-label="治理规则类型">
    <div className="fm-rule-catalog-heading"><strong>治理规则</strong><span>{count} 项</span></div>
    <div className="fm-rule-catalog-list" ref={list}>
      {RULE_CHOICE_GROUPS.map(group => <section key={group.label} aria-label={group.label}>
        <div className="fm-rule-catalog-group">{group.label}<span>{group.options.length}</span></div>
        {group.options.map(choice => <button key={choice.value} ref={mode === choice.value ? selected : undefined} type="button" aria-pressed={mode === choice.value} title={choice.description}
          className={'fm-rule-catalog-item' + (mode === choice.value ? ' is-selected' : '')} onClick={() => onChange(choice.value)}>
          <MappingRuleIcon mode={choice.value} /><span>{choice.label}</span>{mode === choice.value && <CheckCircleOutlined className="fm-rule-catalog-check" aria-hidden="true" />}
        </button>)}
      </section>)}
    </div>
  </nav>;
}
