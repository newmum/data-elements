import React from 'react';
import ReactDOM from 'react-dom/client';
import {App as AntApp} from 'antd';
import {ThemeProvider} from './ThemeProvider';
import App from './App';
import 'antd/dist/reset.css';
import './styles.css';
import './themes.css';
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ThemeProvider><AntApp><App/></AntApp></ThemeProvider></React.StrictMode>);
