import { useRef, useState } from 'react';
import { Button, Input } from 'antd';

/** Stores a small logo as a data URI, or accepts an HTTPS image URL. */
export function ImageValueInput({ value = '', onChange, disabled = false }: {
    value?: string;
    onChange?: (value: string) => void;
    disabled?: boolean;
}) {
    const file = useRef<HTMLInputElement>(null);
    const [error, setError] = useState('');
    const image = /^(data:image\/(png|jpeg|webp|x-icon|svg\+xml);base64,|https:\/\/)/i.test(value) ? value : '';
    const select = (selected?: File) => {
        if (!selected) return;
        if (!['image/png', 'image/jpeg', 'image/webp', 'image/x-icon', 'image/svg+xml'].includes(selected.type) || selected.size > 250 * 1024) {
            setError('请选择不超过 250 KB 的 PNG、JPEG、WebP、ICO 或 SVG 图片。');
            return;
        }
        const reader = new FileReader();
        reader.onload = () => { if (typeof reader.result === 'string') { setError(''); onChange?.(reader.result); } };
        reader.onerror = () => setError('图片读取失败，请重新选择。');
        reader.readAsDataURL(selected);
    };
    return <div className="application-logo-editor">
        <div className="application-logo-editor-row">
            <span className="application-logo-preview">{image ? <img src={image} alt="当前应用标识"/> : '暂无标识'}</span>
            <div><Button disabled={disabled} onClick={() => file.current?.click()}>选择图片</Button>{value && <Button type="link" disabled={disabled} onClick={() => onChange?.('')}>移除标识</Button>}</div>
        </div>
        <input ref={file} type="file" accept="image/png,image/jpeg,image/webp,image/x-icon,image/svg+xml" hidden disabled={disabled} onChange={event => { select(event.target.files?.[0]); event.target.value = ''; }}/>
        <Input disabled={disabled} value={value.startsWith('data:') ? '' : value} placeholder="也可填写 HTTPS 图片地址" onChange={event => { setError(''); onChange?.(event.target.value); }}/>
        {error && <div className="application-logo-error" role="alert">{error}</div>}
    </div>;
}
