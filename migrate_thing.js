const fs = require('fs');
const path = require('path');

const srcBase = "E:\\test\\ncp\\freshmall\\server\\src\\main\\java\\com\\gk\\study";
const destBase = "E:\\test\\ncp\\freshmall\\freshmall-cloud\\freshmall-thing\\src\\main\\java\\com\\freshmall\\thing";
const xmlSrcBase = "E:\\test\\ncp\\freshmall\\server\\src\\main\\resources\\mapper";
const xmlDestBase = "E:\\test\\ncp\\freshmall\\freshmall-cloud\\freshmall-thing\\src\\main\\resources\\mapper";

const modules = ["Thing", "Classification", "ThingCollect", "ThingWish", "Record"];

function replaceContent(content) {
    content = content.replace(/package com\.gk\.study\.controller;/g, "package com.freshmall.thing.controller;");
    content = content.replace(/package com\.gk\.study\.service;/g, "package com.freshmall.thing.service;");
    content = content.replace(/package com\.gk\.study\.service\.impl;/g, "package com.freshmall.thing.service.impl;");
    content = content.replace(/package com\.gk\.study\.mapper;/g, "package com.freshmall.thing.mapper;");

    content = content.replace(/import com\.gk\.study\.controller/g, "import com.freshmall.thing.controller");
    content = content.replace(/import com\.gk\.study\.service/g, "import com.freshmall.thing.service");
    content = content.replace(/import com\.gk\.study\.mapper/g, "import com.freshmall.thing.mapper");
    content = content.replace(/import com\.gk\.study\.entity/g, "import com.freshmall.common.entity");
    content = content.replace(/import com\.gk\.study\.common/g, "import com.freshmall.common");
    content = content.replace(/import com\.gk\.study\.utils/g, "import com.freshmall.common.utils");
    content = content.replace(/import com\.gk\.study\.permission/g, "import com.freshmall.common.permission");

    content = content.replace(/com\.gk\.study\.common\.APIResponse/g, "com.freshmall.common.APIResponse");
    content = content.replace(/com\.gk\.study\.common\.ResponeCode/g, "com.freshmall.common.ResponseCode");
    content = content.replace(/ResponeCode/g, "ResponseCode");

    // For XML mappers
    content = content.replace(/com\.gk\.study\.entity/g, "com.freshmall.common.entity");
    content = content.replace(/com\.gk\.study\.mapper/g, "com.freshmall.thing.mapper");

    return content;
}

function copyAndRefactor(srcPath, destPath) {
    if (!fs.existsSync(srcPath)) {
        console.log(`Source not found: ${srcPath}`);
        return;
    }
    const destDir = path.dirname(destPath);
    if (!fs.existsSync(destDir)) {
        fs.mkdirSync(destDir, { recursive: true });
    }
    let content = fs.readFileSync(srcPath, 'utf-8');
    content = replaceContent(content);
    fs.writeFileSync(destPath, content, 'utf-8');
    console.log(`Copied to ${destPath}`);
}

modules.forEach(mod => {
    copyAndRefactor(path.join(srcBase, `controller\\${mod}Controller.java`), path.join(destBase, `controller\\${mod}Controller.java`));
    copyAndRefactor(path.join(srcBase, `service\\${mod}Service.java`), path.join(destBase, `service\\${mod}Service.java`));
    copyAndRefactor(path.join(srcBase, `service\\impl\\${mod}ServiceImpl.java`), path.join(destBase, `service\\impl\\${mod}ServiceImpl.java`));
    copyAndRefactor(path.join(srcBase, `mapper\\${mod}Mapper.java`), path.join(destBase, `mapper\\${mod}Mapper.java`));
    copyAndRefactor(path.join(xmlSrcBase, `${mod}Mapper.xml`), path.join(xmlDestBase, `${mod}Mapper.xml`));
});

console.log("Done copying files.");
