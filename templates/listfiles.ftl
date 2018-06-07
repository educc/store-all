<!DOCTYPE html>
<html>
<head>
    <title>Store All</title>
</head>
<body>
    <div>
    <#list context.files as filename>
      <p><a href="${filename}">${filename}</a></p>
    </#list>
    </div>
</body>
</html>