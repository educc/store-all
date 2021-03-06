package com.ecacho.storeall;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


@Slf4j
public class StoreAllVerticle extends AbstractVerticle {
    String basePath;
    FreeMarkerTemplateEngine engine;

    private static final String DEFAULT_PATH="DEFAULT_PATH";
    private static final String SERVER_PORT="SERVER_PORT";

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        startHttpServer().setHandler(startFuture);
    }

    public Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();

        basePath = config().getString(DEFAULT_PATH, "D:\\temp");
        int port = config().getInteger(SERVER_PORT,8080);

        this.engine = FreeMarkerTemplateEngine.create();

        Router router = Router.router(vertx);
        router.getWithRegex(".*").handler(this::listFilesHandler);
        router.getWithRegex(".*").handler(this::renderFilesPage);
        router.postWithRegex(".*").handler(BodyHandler.create());
        router.postWithRegex(".*").handler(this::saveFileHandler);

        server
            .requestHandler(router::accept)
            .listen(port, ar -> {
                if (ar.succeeded()) {
                    log.info("HTTP server running on port 8080");
                    future.complete();
                } else {
                    log.error("Could not start a HTTP server", ar.cause());
                    future.fail(ar.cause());
                }
            });
        return future;
    }

    private void listFilesHandler(RoutingContext context){
        String relative = context.request().uri();
        Path abspath = Paths.get(this.basePath, relative);
        System.out.println(abspath.toString());
        context.response().setChunked(true);
        if(Files.exists(abspath)){
            if(Files.isDirectory(abspath)){
                List<String> files = new ArrayList<>();
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(abspath)) {
                    for (Path path : directoryStream) {
                        files.add(path.getFileName().toString());
                    }
                } catch (IOException ex) {}

                context.put("files", files);
                context.next();
            }else{
                context.response()
                        .sendFile(abspath.toString());
            }
        }else{
            context.response().end("File Not Found");
        }
    }

    private void renderFilesPage(RoutingContext ctx){
      this.engine.render(ctx, "templates/", "listfiles.ftl", res -> {
        if(res.failed()){
          ctx.response()
                  .write(res.cause().getMessage())
                  .end();
          return;
        }

        ctx.response()
                .putHeader("content-type", "text/html")
                .write(res.result())
                .end();
      });
    }

    private void saveFileHandler(RoutingContext context){
        String relative = context.request().uri();
        Path abspath = Paths.get(this.basePath, relative);

        Set<FileUpload> fileUploadSet = context.fileUploads();
        Iterator<FileUpload> fileUploadIterator = fileUploadSet.iterator();
        while (fileUploadIterator.hasNext()){
            FileUpload fileUpload = fileUploadIterator.next();

            // To get the uploaded file do
            Buffer uploadedFile = vertx.fileSystem().readFileBlocking(fileUpload.uploadedFileName());

            vertx.fileSystem().writeFile(
                    abspath.resolve(fileUpload.fileName()).toString() ,
                    uploadedFile,ar -> {
                context.response().setChunked(true);
                if(ar.succeeded()){
                    context.response().end("file saved");
                }else{
                    context.response().end("error: " + ar.cause().getMessage());
                }
            });
        }


    }
}
