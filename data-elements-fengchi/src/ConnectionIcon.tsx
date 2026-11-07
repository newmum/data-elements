/** Preserved approved plug artwork from published version 3. */
export default function ConnectionIcon({size=32,className=''}:{size?:number;className?:string}){
 const stroke=size<=18?2:1.5;
 return <svg width={size} height={size} viewBox="0 0 32 32" className={`native-icon ${className}`} aria-hidden="true" focusable="false" fill="none" stroke="#427fa5" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" style={{flexShrink:0,verticalAlign:'middle'}}><g stroke="#667b8c"><path d="M11 3v5m8-5v5M7 8h16v5a8 8 0 0 1-16 0ZM15 21v8" fill="#e4eaef"/><path d="M11 12h8" fill="none"/><g><circle cx="25" cy="25" r="6" fill="#638b58" stroke="#fbfdfe" strokeWidth="2"/><path d="M25 22v6m-3-3h6" fill="none" stroke="#fbfdfe" strokeWidth="2"/></g></g></svg>;
}
