/* 用例/知识库文件解析：CSV · XLSX · XMind */
(function initP0FileParsers(global) {
  const CASE_NAME_KEYS = ['casename', 'case_name', '用例名称', '用例名', 'name', 'title', '标题', '用例'];
  const STEP_KEYS = ['step', 'steps', '步骤', '操作步骤', '测试步骤'];
  const EXPECT_KEYS = ['expected_results', 'expected', '预期结果', '期望结果', '预期', '期望'];
  const SCENE_KEYS = ['scene', '场景', '业务场景', '流程节点', '节点'];
  const FEATURE_KEYS = ['function_point', 'feature', '功能点', 'feature_name', '功能', '模块功能'];
  const MODULE_KEYS = ['module', 'belong_platform', '模块', '平台', '所属模块', 'belong_module'];

  function compact(text) {
    return String(text || '').replace(/\s+/g, ' ').trim();
  }

  function normalizeHeader(value) {
    return compact(value).toLowerCase().replace(/[\s_\-]/g, '');
  }

  function findHeaderIndex(headers, keys) {
    const normalizedKeys = keys.map(normalizeHeader);
    for (let index = 0; index < headers.length; index += 1) {
      const header = normalizeHeader(headers[index]);
      if (!header) continue;
      if (normalizedKeys.includes(header)) return index;
      if (normalizedKeys.some((key) => header.includes(key) || key.includes(header))) return index;
    }
    return -1;
  }

  function rowFromCells(cells, map, rowIndex) {
    const caseName = compact(cells[map.nameIdx] || '');
    if (!caseName) return null;
    return {
      caseName: caseName || ('未命名用例-' + (rowIndex + 1)),
      step: map.stepIdx >= 0 ? compact(cells[map.stepIdx] || '') : '',
      expected: map.expectIdx >= 0 ? compact(cells[map.expectIdx] || '') : '',
      scene: map.sceneIdx >= 0 ? compact(cells[map.sceneIdx] || '') : '',
      feature: map.featureIdx >= 0 ? compact(cells[map.featureIdx] || '') : '',
      module: map.moduleIdx >= 0 ? compact(cells[map.moduleIdx] || '') : ''
    };
  }

  function buildHeaderMap(headers) {
    const hasHeader = headers.some((header) => {
      const value = normalizeHeader(header);
      return CASE_NAME_KEYS.some((key) => value.includes(normalizeHeader(key)));
    });
    return {
      hasHeader: hasHeader,
      nameIdx: hasHeader ? Math.max(0, findHeaderIndex(headers, CASE_NAME_KEYS)) : 0,
      stepIdx: hasHeader ? findHeaderIndex(headers, STEP_KEYS) : 1,
      expectIdx: hasHeader ? findHeaderIndex(headers, EXPECT_KEYS) : 2,
      sceneIdx: hasHeader ? findHeaderIndex(headers, SCENE_KEYS) : 3,
      featureIdx: hasHeader ? findHeaderIndex(headers, FEATURE_KEYS) : -1,
      moduleIdx: hasHeader ? findHeaderIndex(headers, MODULE_KEYS) : -1
    };
  }

  function parseCsvText(text) {
    const lines = String(text || '').split(/\r?\n/).filter((line) => line.trim());
    if (!lines.length) return [];
    const splitCsvLine = function(line) {
      const cells = [];
      let current = '';
      let inQuotes = false;
      for (let index = 0; index < line.length; index += 1) {
        const char = line[index];
        if (char === '"') {
          inQuotes = !inQuotes;
          continue;
        }
        if (char === ',' && !inQuotes) {
          cells.push(current.trim());
          current = '';
          continue;
        }
        current += char;
      }
      cells.push(current.trim());
      return cells;
    };
    const headers = splitCsvLine(lines[0]);
    const map = buildHeaderMap(headers);
    const startIndex = map.hasHeader ? 1 : 0;
    return lines.slice(startIndex).map((line, rowIndex) => {
      return rowFromCells(splitCsvLine(line), map, rowIndex);
    }).filter(Boolean);
  }

  function parseXlsxArrayBuffer(buffer) {
    if (!global.XLSX) {
      throw new Error('未加载 SheetJS（XLSX），无法解析 Excel');
    }
    const workbook = global.XLSX.read(buffer, { type: 'array' });
    const sheetName = workbook.SheetNames[0];
    if (!sheetName) return { rows: [], meta: { sheetName: '', sheetCount: 0 } };
    const sheet = workbook.Sheets[sheetName];
    const matrix = global.XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '', raw: false });
    if (!matrix.length) return { rows: [], meta: { sheetName: sheetName, sheetCount: workbook.SheetNames.length } };
    const headers = (matrix[0] || []).map((cell) => String(cell || ''));
    const map = buildHeaderMap(headers);
    const startIndex = map.hasHeader ? 1 : 0;
    const rows = matrix.slice(startIndex).map((cells, rowIndex) => {
      const normalized = (cells || []).map((cell) => String(cell == null ? '' : cell));
      return rowFromCells(normalized, map, rowIndex);
    }).filter(Boolean);
    return {
      rows: rows,
      meta: {
        sheetName: sheetName,
        sheetCount: workbook.SheetNames.length,
        format: 'xlsx'
      }
    };
  }

  function topicTitle(topic) {
    if (!topic) return '';
    if (typeof topic.title === 'string') return compact(topic.title);
    if (topic.title && typeof topic.title === 'object') {
      return compact(topic.title.text || topic.title.content || '');
    }
    return compact(topic['@title'] || '');
  }

  function topicNotes(topic) {
    if (!topic || !topic.notes) return '';
    const notes = topic.notes;
    if (typeof notes === 'string') return compact(notes);
    if (notes.plain && typeof notes.plain.content === 'string') return compact(notes.plain.content);
    if (notes.plain && typeof notes.plain === 'string') return compact(notes.plain);
    if (notes.content) return compact(notes.content);
    return '';
  }

  function jsonChildren(topic) {
    const children = (topic && topic.children) || {};
    const bag = [];
    ['attached', 'detached', 'summary'].forEach((key) => {
      const list = children[key];
      if (Array.isArray(list)) bag.push.apply(bag, list);
    });
    return bag;
  }

  function walkJsonTopic(topic, path, visit) {
    const title = topicTitle(topic);
    const nextPath = title ? path.concat([title]) : path.slice();
    const kids = jsonChildren(topic);
    visit({
      title: title,
      notes: topicNotes(topic),
      path: nextPath,
      depth: nextPath.length,
      isLeaf: !kids.length,
      labels: Array.isArray(topic.labels) ? topic.labels : []
    });
    kids.forEach((child) => walkJsonTopic(child, nextPath, visit));
  }

  function parseContentJson(text) {
    const data = JSON.parse(text);
    let sheets = [];
    if (Array.isArray(data)) sheets = data;
    else if (data && Array.isArray(data.sheets)) sheets = data.sheets;
    else if (data && data.rootTopic) sheets = [data];
    else throw new Error('无法识别 content.json 结构');
    const nodes = [];
    sheets.forEach((sheet) => {
      if (!sheet || !sheet.rootTopic) return;
      const sheetTitle = compact(sheet.title || '');
      walkJsonTopic(sheet.rootTopic, sheetTitle ? [sheetTitle] : [], function(node) {
        nodes.push(node);
      });
    });
    return nodes;
  }

  function xmlLocalName(node) {
    return String((node && (node.localName || node.nodeName)) || '').replace(/^.*:/, '').toLowerCase();
  }

  function xmlChildrenTopics(topicEl) {
    const result = [];
    if (!topicEl || !topicEl.children) return result;
    Array.prototype.forEach.call(topicEl.children, (child) => {
      if (xmlLocalName(child) !== 'children') return;
      Array.prototype.forEach.call(child.children || [], (topicsEl) => {
        if (xmlLocalName(topicsEl) !== 'topics') return;
        Array.prototype.forEach.call(topicsEl.children || [], (topic) => {
          if (xmlLocalName(topic) === 'topic') result.push(topic);
        });
      });
    });
    return result;
  }

  function xmlTopicTitle(topicEl) {
    if (!topicEl) return '';
    const attr = topicEl.getAttribute && topicEl.getAttribute('title');
    if (attr) return compact(attr);
    let title = '';
    Array.prototype.forEach.call(topicEl.children || [], (child) => {
      if (xmlLocalName(child) === 'title') title = compact(child.textContent || '');
    });
    return title;
  }

  function xmlTopicNotes(topicEl) {
    let notes = '';
    Array.prototype.forEach.call(topicEl.children || [], (child) => {
      if (xmlLocalName(child) !== 'notes') return;
      notes = compact(child.textContent || '');
    });
    return notes;
  }

  function walkXmlTopic(topicEl, path, visit) {
    const title = xmlTopicTitle(topicEl);
    const nextPath = title ? path.concat([title]) : path.slice();
    const kids = xmlChildrenTopics(topicEl);
    visit({
      title: title,
      notes: xmlTopicNotes(topicEl),
      path: nextPath,
      depth: nextPath.length,
      isLeaf: !kids.length,
      labels: []
    });
    kids.forEach((child) => walkXmlTopic(child, nextPath, visit));
  }

  function parseContentXml(text) {
    const doc = new DOMParser().parseFromString(text, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('content.xml 解析失败');
    const topicNodes = [];
    Array.prototype.forEach.call(doc.getElementsByTagName('*'), (el) => {
      if (xmlLocalName(el) === 'topic') topicNodes.push(el);
    });
    // 取每个 sheet 的根 topic：父级不是 topic 的 topic
    const roots = topicNodes.filter((el) => {
      let parent = el.parentElement;
      while (parent) {
        if (xmlLocalName(parent) === 'topic') return false;
        parent = parent.parentElement;
      }
      return true;
    });
    const nodes = [];
    roots.forEach((root) => walkXmlTopic(root, [], function(node) { nodes.push(node); }));
    return nodes;
  }

  function nodesToCaseRows(nodes) {
    const leaves = (nodes || []).filter((node) => node.isLeaf && node.title && node.depth >= 2);
    const source = leaves.length ? leaves : (nodes || []).filter((node) => node.title && node.depth >= 2);
    return source.map((node, index) => {
      const path = node.path || [];
      const feature = path.length >= 2 ? path[path.length - 2] : '';
      const scene = path.length >= 3 ? path[path.length - 3] : (path[0] || '');
      const notes = compact(node.notes || '');
      let step = '';
      let expected = '';
      if (notes) {
        const parts = notes.split(/预期[:：]|期望[:：]|结果[:：]/);
        step = compact(parts[0] || '');
        expected = compact(parts[1] || '');
        if (!expected && /步骤/.test(notes)) {
          step = notes;
        } else if (!step) {
          step = notes;
        }
      }
      return {
        caseName: node.title,
        step: step,
        expected: expected,
        scene: scene,
        feature: feature,
        module: path[0] || ''
      };
    }).filter((row) => row.caseName);
  }

  function nodesToKnowledgeMarkdown(nodes) {
    const byDepth1 = {};
    (nodes || []).forEach((node) => {
      if (!node.title || node.depth < 2) return;
      const scene = node.path[0] || '未命名画布';
      const feature = node.depth >= 2 ? node.path[1] : '';
      if (!byDepth1[scene]) byDepth1[scene] = { features: [], rules: [] };
      if (node.depth === 2 && feature) byDepth1[scene].features.push(feature);
      if (node.depth >= 3) {
        const leaf = node.path[node.path.length - 1];
        if (/必须|不可|不能|应当|禁止|校验|拦截/.test(leaf)) byDepth1[scene].rules.push(leaf);
        else byDepth1[scene].features.push(leaf);
      }
      if (node.notes && /必须|不可|不能|应当|禁止/.test(node.notes)) {
        byDepth1[scene].rules.push(compact(node.notes).slice(0, 28));
      }
    });
    const scenes = Object.keys(byDepth1);
    const features = [];
    const rules = [];
    scenes.forEach((scene) => {
      features.push.apply(features, byDepth1[scene].features);
      rules.push.apply(rules, byDepth1[scene].rules);
    });
    const unique = function(list) {
      const seen = new Set();
      return list.filter((item) => {
        const text = compact(item);
        if (!text || seen.has(text)) return false;
        seen.add(text);
        return true;
      });
    };
    const sceneList = unique(scenes);
    const featureList = unique(features);
    const ruleList = unique(rules);
    return [
      '# 从 XMind 导入的知识骨架',
      '',
      '## 业务场景',
      ...sceneList.map((item) => '- ' + item),
      '',
      '## 功能点',
      ...featureList.map((item) => '- ' + item),
      '',
      '## 规则',
      ...ruleList.map((item) => '- ' + item)
    ].join('\n');
  }

  function parseXmindArrayBuffer(buffer) {
    if (!global.JSZip) throw new Error('未加载 JSZip，无法解析 XMind');
    return global.JSZip.loadAsync(buffer).then(function(zip) {
      const jsonFile = zip.file('content.json');
      if (jsonFile) {
        return jsonFile.async('string').then(function(text) {
          const nodes = parseContentJson(text);
          return {
            nodes: nodes,
            rows: nodesToCaseRows(nodes),
            knowledgeText: nodesToKnowledgeMarkdown(nodes),
            meta: { format: 'xmind-json', nodeCount: nodes.length }
          };
        });
      }
      const xmlFile = zip.file('content.xml');
      if (xmlFile) {
        return xmlFile.async('string').then(function(text) {
          const nodes = parseContentXml(text);
          return {
            nodes: nodes,
            rows: nodesToCaseRows(nodes),
            knowledgeText: nodesToKnowledgeMarkdown(nodes),
            meta: { format: 'xmind-xml', nodeCount: nodes.length }
          };
        });
      }
      throw new Error('未找到 content.json / content.xml，可能不是有效 XMind 包');
    });
  }

  function readAsArrayBuffer(file) {
    return new Promise(function(resolve, reject) {
      const reader = new FileReader();
      reader.onload = function() { resolve(reader.result); };
      reader.onerror = function() { reject(new Error('读取文件失败')); };
      reader.readAsArrayBuffer(file);
    });
  }

  function readAsText(file) {
    return new Promise(function(resolve, reject) {
      const reader = new FileReader();
      reader.onload = function() { resolve(String(reader.result || '')); };
      reader.onerror = function() { reject(new Error('读取文件失败')); };
      reader.readAsText(file);
    });
  }

  function parseCasesFile(file) {
    const name = String(file && file.name || '').toLowerCase();
    if (name.endsWith('.csv') || name.endsWith('.txt')) {
      return readAsText(file).then(function(text) {
        return {
          rows: parseCsvText(text),
          meta: { format: 'csv', fileName: file.name }
        };
      });
    }
    if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      return readAsArrayBuffer(file).then(function(buffer) {
        const parsed = parseXlsxArrayBuffer(buffer);
        return {
          rows: parsed.rows,
          meta: Object.assign({ fileName: file.name }, parsed.meta)
        };
      });
    }
    if (name.endsWith('.xmind')) {
      return readAsArrayBuffer(file).then(function(buffer) {
        return parseXmindArrayBuffer(buffer).then(function(parsed) {
          return {
            rows: parsed.rows,
            meta: Object.assign({ fileName: file.name }, parsed.meta),
            knowledgeText: parsed.knowledgeText
          };
        });
      });
    }
    return Promise.reject(new Error('暂不支持该用例文件格式：' + (file && file.name || '')));
  }

  function parseKnowledgeFile(file) {
    const name = String(file && file.name || '').toLowerCase();
    if (name.endsWith('.txt') || name.endsWith('.md') || name.endsWith('.markdown')) {
      return readAsText(file).then(function(text) {
        return { text: text, meta: { format: 'text', fileName: file.name } };
      });
    }
    if (name.endsWith('.xmind')) {
      return readAsArrayBuffer(file).then(function(buffer) {
        return parseXmindArrayBuffer(buffer).then(function(parsed) {
          return {
            text: parsed.knowledgeText,
            meta: Object.assign({ fileName: file.name }, parsed.meta)
          };
        });
      });
    }
    if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      return readAsArrayBuffer(file).then(function(buffer) {
        if (!global.XLSX) throw new Error('未加载 SheetJS（XLSX）');
        const workbook = global.XLSX.read(buffer, { type: 'array' });
        const sheetName = workbook.SheetNames[0];
        const sheet = workbook.Sheets[sheetName];
        const matrix = global.XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '', raw: false });
        const lines = matrix.map(function(row) {
          return (row || []).map(function(cell) { return String(cell || '').trim(); }).filter(Boolean).join('、');
        }).filter(Boolean);
        return {
          text: '# 从 Excel 导入的知识草稿\n\n' + lines.map(function(line) { return '- ' + line; }).join('\n'),
          meta: { format: 'xlsx', fileName: file.name, sheetName: sheetName }
        };
      });
    }
    return Promise.reject(new Error('暂不支持该知识库文件格式：' + (file && file.name || '')));
  }

  global.P0FileParsers = {
    parseCsvText: parseCsvText,
    parseCasesFile: parseCasesFile,
    parseKnowledgeFile: parseKnowledgeFile,
    parseXlsxArrayBuffer: parseXlsxArrayBuffer,
    parseXmindArrayBuffer: parseXmindArrayBuffer
  };
})(window);
