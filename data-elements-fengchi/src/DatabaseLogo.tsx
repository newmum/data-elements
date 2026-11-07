import mysql from './assets/database/mysql.svg';
import postgresql from './assets/database/postgresql.svg';
import sqlserver from './assets/database/sqlserver.svg';
import oracle from './assets/database/oracle.svg';
import sqlite from './assets/database/sqlite.svg';
import mariadb from './assets/database/mariadb.svg';
import mongodb from './assets/database/mongodb.svg';
import redis from './assets/database/redis.svg';
import snowflake from './assets/database/snowflake.svg';

export interface DatabaseLogoProps {
  type: string;
  size?: number;
}

const logos: Record<string, string> = {
  mysql,
  postgresql,
  postgres: postgresql,
  sqlserver,
  microsoftsqlserver: sqlserver,
  mssql: sqlserver,
  oracle,
  sqlite,
  mariadb,
  mongodb,
  redis,
  snowflake,
};

/** Locally bundled brand artwork. Provenance and licenses: THIRD_PARTY_NOTICES.md. */
export default function DatabaseLogo({ type, size = 32 }: DatabaseLogoProps) {
  const key = type.toLowerCase().replace(/[\s_-]/g, '');
  const src = logos[key];
  if (!src) return null;

  // Upstream square viewBoxes preserve the full marks and their aspect ratios.
  // Decorative: the adjacent connection-type text supplies the accessible name.
  return (
    <img
      src={src}
      width={size}
      height={size}
      className="native-icon database-logo"
      alt=""
      aria-hidden="true"
      draggable={false}
      style={{ display: 'inline-block', flexShrink: 0, objectFit: 'contain', verticalAlign: 'middle' }}
    />
  );
}
