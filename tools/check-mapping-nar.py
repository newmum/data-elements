#!/usr/bin/env python3
"""Refuse NARs with duplicate bytecode or missing mapping processor/service registrations."""
import argparse
import io
import json
import zipfile
from collections import defaultdict


def check(path):
    classes=defaultdict(list)
    jars=[]
    registrations=set()
    with zipfile.ZipFile(path) as nar:
        for name in nar.namelist():
            if not name.startswith('META-INF/bundled-dependencies/') or not name.endswith('.jar'):
                continue
            jars.append(name)
            with zipfile.ZipFile(io.BytesIO(nar.read(name))) as jar:
                for entry in jar.namelist():
                    if entry.endswith('.class') and not entry.endswith('module-info.class'):
                        classes[entry].append(name)
                    if entry.startswith('META-INF/services/'):
                        registrations.update(jar.read(entry).decode('utf-8').splitlines())
    duplicates={name:owners for name,owners in classes.items() if len(owners)>1}
    if duplicates:
        raise ValueError('Duplicate class definitions: '+json.dumps(dict(list(duplicates.items())[:5])))
    expected={'com.linewell.dataelement.nifi.HaitongTransformRecord','com.linewell.dataelement.nifi.JdbcHaitongDictionaryService'}
    if not expected.issubset(registrations):
        raise ValueError('Processor/controller service registration missing')
    if sum('haitong-mapping-core-' in name for name in jars)!=1 or sum('haitong-mapping-runtime-' in name for name in jars)!=1:
        raise ValueError('Expected exactly one core and runtime JAR; build clean package')
    return {'jarCount':len(jars),'classCount':len(classes),'duplicateClasses':0,'registrationsVerified':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('nar')
    args=parser.parse_args()
    print(json.dumps(check(args.nar)))
