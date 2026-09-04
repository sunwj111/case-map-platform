/* 自然语言知识库抽取：场景 / 功能点 / 规则候选（可人工修改） */
(function initP0KbExtract(global) {
  const STOP = new Set([
    '以及', '或者', '并且', '然后', '进行', '可以', '需要', '如果', '当', '的', '了', '是', '在',
    '我们', '系统', '用户', '测试', '用例', '知识库', '流程', '业务', '说明', '如下', '包括',
    '主要', '相关', '一般', '时候', '之后', '之前', '通过', '完成', '支持'
  ]);

  function compact(text) {
    return String(text || '').replace(/\s+/g, ' ').trim();
  }

  function unique(list) {
    const seen = new Set();
    const result = [];
    list.forEach((item) => {
      const text = compact(item);
      if (!text || seen.has(text)) return;
      seen.add(text);
      result.push(text);
    });
    return result;
  }

  function cleanItem(raw) {
    let text = compact(raw)
      .replace(/^(?:包括|涵盖|主要有|主要是)[:：]?\s*/, '')
      .replace(/^[-*+•·\d.、)\]】\s]+/, '')
      .replace(/^[\(（]?\d+[\)）\.、]\s*/, '')
      .replace(/^[Rｒ]\d+[\s:：-]*/, '')
      .replace(/[:：]\s*$/, '')
      .replace(/[。；;]+$/, '');
    if (text.length < 2 || text.length > 28) return '';
    if (STOP.has(text)) return '';
    if (/^(注|说明|例如|比如)/.test(text)) return '';
    return text;
  }

  function pushItems(bag, values, source) {
    (values || []).forEach((value) => {
      const text = cleanItem(value);
      if (!text) return;
      bag.push({ text: text, source: source || '抽取' });
    });
  }

  function parseBySections(text) {
    const scenes = [];
    const features = [];
    const rules = [];
    const sectionPattern = /^#{1,3}\s*(.+)\s*$/gm;
    const sections = [];
    let match;
    while ((match = sectionPattern.exec(text)) !== null) {
      sections.push({ title: match[1].trim(), start: match.index + match[0].length });
    }
    if (!sections.length) return { scenes: scenes, features: features, rules: rules, hasSections: false };

    for (let index = 0; index < sections.length; index += 1) {
      const section = sections[index];
      const end = index + 1 < sections.length ? sections[index + 1].start : text.length;
      const body = text.slice(section.start, end);
      const items = body
        .split(/\r?\n/)
        .map((line) => cleanItem(line.replace(/^[-*+\d.、)\s]+/, '')))
        .filter(Boolean);
      const title = section.title;
      if (/场景|流程|环节|阶段/.test(title)) pushItems(scenes, items, '章节·场景');
      else if (/功能|能力|模块|节点/.test(title)) pushItems(features, items, '章节·功能点');
      else if (/规则|状态|约束|校验|判定/.test(title)) pushItems(rules, items, '章节·规则');
      else {
        // 未知章节：短条目偏功能点，长条目偏规则
        items.forEach((item) => {
          if (item.length <= 12) pushItems(features, [item], '章节·其他');
          else pushItems(rules, [item], '章节·其他');
        });
      }
    }
    return { scenes: scenes, features: features, rules: rules, hasSections: true };
  }

  function splitListBlob(blob) {
    return String(blob || '')
      .split(/[、，,；;\/\|]|(?:和(?!同))|(?:与(?!外))/)
      .map(cleanItem)
      .filter(Boolean);
  }

  function extractFromFreeText(text) {
    const scenes = [];
    const features = [];
    const rules = [];
    const lines = String(text || '').split(/\r?\n/);

    // 1) 行级：场景/功能/规则引导语
    lines.forEach((line) => {
      const raw = compact(line);
      if (!raw) return;
      const sceneLead = raw.match(/^(?:业务)?场景[:：]\s*(.+)$/);
      const featureLead = raw.match(/^(?:功能点|功能|能力|模块)[:：]\s*(.+)$/);
      const ruleLead = raw.match(/^(?:规则|约束|校验)[:：]\s*(.+)$/);
      if (sceneLead) {
        pushItems(scenes, splitListBlob(sceneLead[1]), '行首·场景');
        return;
      }
      if (featureLead) {
        pushItems(features, splitListBlob(featureLead[1]), '行首·功能点');
        return;
      }
      if (ruleLead) {
        pushItems(rules, splitListBlob(ruleLead[1]), '行首·规则');
        return;
      }
      const supportLead = raw.match(/(?:系统)?支持[:：]?\s*([^。；;\n]{4,80})/);
      if (supportLead && /[、,，]/.test(supportLead[1])) {
        pushItems(features, splitListBlob(supportLead[1]), '行首·功能点');
      }
      if (/^[-*+•·]\s*/.test(line) || /^\d+[\.、)]\s*/.test(line)) {
        const item = cleanItem(raw);
        if (!item) return;
        if (/必须|不可|不能|应当|禁止|校验|拦截|为空|非法/.test(item)) pushItems(rules, [item], '列表·规则');
        else if (/场景|流程|环节|阶段|审核|提交|生成|汇总|计算|触发/.test(item) && item.length <= 16) {
          pushItems(scenes, [item], '列表·场景');
        } else if (item.length <= 14) {
          pushItems(features, [item], '列表·功能点');
        }
      }
    });

    // 2) 句式：包括/涵盖/链路 A、B、C
    const includePattern = /(?:包括|涵盖|链路(?:为|是)?|流程(?:为|是)?|环节(?:有|包括)?|主要(?:有|包括))[:：]?\s*([^\n。；;]{4,80})/g;
    let includeMatch;
    while ((includeMatch = includePattern.exec(text)) !== null) {
      const parts = splitListBlob(includeMatch[1]);
      parts.forEach((part) => {
        if (/必须|不可|不能|应当|禁止|校验/.test(part)) pushItems(rules, [part], '句式·规则');
        else if (part.length <= 16) pushItems(scenes, [part], '句式·场景');
        else pushItems(features, [part.slice(0, 18)], '句式·功能点');
      });
    }

    // 3) 功能点句式：「支持/提供/完成 XXX」
    const featurePattern = /(?:支持|提供|完成|实现|负责|用于)\s*([\u4e00-\u9fa5A-Za-z0-9]{2,16})/g;
    let featureMatch;
    while ((featureMatch = featurePattern.exec(text)) !== null) {
      const token = cleanItem(featureMatch[1]);
      if (token && !/系统|平台|用户|数据/.test(token)) pushItems(features, [token], '句式·功能点');
    }

    // 4) 规则句式
    const rulePattern = /([\u4e00-\u9fa5A-Za-z0-9]{2,20}(?:必须|不可|不能|应当|禁止)[^\n。；;]{0,20})/g;
    let ruleMatch;
    while ((ruleMatch = rulePattern.exec(text)) !== null) {
      pushItems(rules, [ruleMatch[1]], '句式·规则');
    }

    // 5) 「名词+与+名词」「名词+和+名词」短链路
    const chainPattern = /([\u4e00-\u9fa5]{2,8})(?:与|和|→|->|—>|－>)([\u4e00-\u9fa5]{2,8})/g;
    let chainMatch;
    while ((chainMatch = chainPattern.exec(text)) !== null) {
      pushItems(scenes, [chainMatch[1], chainMatch[2], chainMatch[1] + '与' + chainMatch[2]], '句式·链路');
    }

    return { scenes: scenes, features: features, rules: rules };
  }

  function toPlainList(items) {
    return unique((items || []).map((item) => (typeof item === 'string' ? item : item.text)));
  }

  function mergeBags() {
    const map = new Map();
    Array.prototype.slice.call(arguments).forEach((bag) => {
      (bag || []).forEach((item) => {
        const text = typeof item === 'string' ? cleanItem(item) : cleanItem(item.text);
        if (!text) return;
        const source = typeof item === 'string' ? '抽取' : (item.source || '抽取');
        if (!map.has(text)) map.set(text, { text: text, source: source });
      });
    });
    return Array.from(map.values());
  }

  function preferLongerLabels(items) {
    const texts = toPlainList(items);
    const kept = texts.filter((text) => {
      if (text.length < 3) return false;
      if (/^(包括|涵盖|流程|环节|主要|支持|提供|完成)$/.test(text)) return false;
      // 若存在更长且包含自己的标签，丢掉短碎片
      const coveredByLonger = texts.some((other) => other !== text && other.includes(text) && other.length >= text.length + 2);
      return !coveredByLonger;
    });
    const sourceMap = new Map();
    (items || []).forEach((item) => {
      const text = typeof item === 'string' ? cleanItem(item) : cleanItem(item.text);
      const source = typeof item === 'string' ? '抽取' : (item.source || '抽取');
      if (text) sourceMap.set(text, source);
    });
    return kept.map((text) => ({ text: text, source: sourceMap.get(text) || '抽取' }));
  }

  function extractKnowledge(text) {
    const sourceText = String(text || '');
    const sectioned = parseBySections(sourceText);
    const free = extractFromFreeText(sourceText);
    let scenes = preferLongerLabels(mergeBags(sectioned.scenes, free.scenes)).slice(0, 24);
    let features = preferLongerLabels(mergeBags(sectioned.features, free.features)).slice(0, 30);
    let rules = preferLongerLabels(mergeBags(sectioned.rules, free.rules)).slice(0, 24);

    // 去交叉：同时出现在场景和功能点时，含流程动词的留场景
    const sceneSet = new Set(scenes.map((item) => item.text));
    features = features.filter((item) => {
      if (!sceneSet.has(item.text)) return true;
      return !/场景|流程|环节|阶段|审核|提交/.test(item.text);
    });

    return {
      scenes: scenes,
      features: features,
      rules: rules,
      plain: {
        scenes: toPlainList(scenes),
        features: toPlainList(features),
        rules: toPlainList(rules)
      },
      meta: {
        hasSections: !!sectioned.hasSections,
        sceneCount: scenes.length,
        featureCount: features.length,
        ruleCount: rules.length,
        mode: sectioned.hasSections ? 'structured+nl' : 'nl'
      }
    };
  }

  function itemsToEditable(groups) {
    return {
      scene: (groups.scenes || []).map((item) => ({ text: item.text, source: item.source || '抽取' })),
      feature: (groups.features || []).map((item) => ({ text: item.text, source: item.source || '抽取' })),
      rule: (groups.rules || []).map((item) => ({ text: item.text, source: item.source || '抽取' }))
    };
  }

  global.P0KbExtract = {
    extractKnowledge: extractKnowledge,
    itemsToEditable: itemsToEditable,
    toPlainList: toPlainList,
    cleanItem: cleanItem
  };
})(window);
